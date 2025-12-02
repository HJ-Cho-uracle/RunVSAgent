#!/bin/bash

# RunVSAgent 프로젝트 설정 스크립트
# 이 스크립트는 개발 환경 및 의존성을 초기화합니다.

# 오류 발생 시 즉시 종료, 정의되지 않은 변수 사용 시 오류, 파이프라인 오류 시 오류
set -euo pipefail

# 공통 유틸리티 스크립트 로드
source "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/lib/common.sh"
source "$SCRIPT_DIR/lib/build.sh" # 빌드 관련 함수 (init_submodules 등)를 사용하기 위해 로드

# --- 스크립트 설정 ---
readonly SCRIPT_NAME="setup.sh"
readonly SCRIPT_VERSION="1.0.0"
readonly PATCH_FILE="deps/patches/vscode/feature-cline-ai.patch" # 적용할 패치 파일 경로

# --- 설정 옵션 ---
FORCE_REINSTALL=false   # 의존성 강제 재설치 여부
SKIP_SUBMODULES=false   # Git 서브모듈 초기화 건너뛰기 여부
SKIP_DEPENDENCIES=false # 의존성 설치 건너뛰기 여부
APPLY_PATCHES=true      # 패치 적용 여부

# --- 스크립트 도움말 표시 ---
show_help() {
    cat << EOF
$SCRIPT_NAME - RunVSAgent 개발 환경 설정

사용법:
    $SCRIPT_NAME [옵션]

설명:
    이 스크립트는 다음을 통해 개발 환경을 초기화합니다:
    - 시스템 요구 사항 유효성 검사 (Git LFS 포함)
    - 대용량 파일 처리를 위한 Git LFS 설정
    - Git 서브모듈 초기화
    - 프로젝트 의존성 설치
    - 필요한 패치 적용
    - 빌드 환경 설정

옵션:
    -f, --force           의존성 강제 재설치
    -s, --skip-submodules Git 서브모듈 초기화 건너뛰기
    -d, --skip-deps       의존성 설치 건너뛰기
    -p, --no-patches      패치 적용 건너뛰기
    -v, --verbose         자세한 출력 활성화
    -n, --dry-run         실행하지 않고 수행될 작업 표시
    -h, --help            이 도움말 메시지 표시

예시:
    $SCRIPT_NAME                    # 전체 설정
    $SCRIPT_NAME --force            # 모든 것 강제 재설치
    $SCRIPT_NAME --skip-deps        # 의존성 설치 건너뛰기
    $SCRIPT_NAME --verbose          # 자세한 출력

환경 변수:
    NODE_VERSION_MIN    최소 Node.js 버전 (기본값: 16.0.0)
    SKIP_VALIDATION     'true'로 설정하면 환경 유효성 검사 건너뛰기

요구 사항:
    - 대용량 파일 처리를 위해 Git LFS가 설치되어 있어야 합니다.
    - 설정이 실패하면 'git lfs install'을 수동으로 실행하세요.

종료 코드:
    0    성공
    1    일반 오류
    2    환경 유효성 검사 실패
    3    의존성 설치 실패
    4    패치 적용 실패

EOF
}

# --- 명령줄 인자 파싱 ---
parse_setup_args() {
    while [[ $# -gt 0 ]]; do
        case $1 in
            -f|--force)
                FORCE_REINSTALL=true
                shift
                ;;
            -s|--skip-submodules)
                SKIP_SUBMODULES=true
                shift
                ;;
            -d|--skip-deps)
                SKIP_DEPENDENCIES=true
                shift
                ;;
            -p|--no-patches)
                APPLY_PATCHES=false
                shift
                ;;
            -v|--verbose)
                VERBOSE=true
                shift
                ;;
            -n|--dry-run)
                DRY_RUN=true
                shift
                ;;
            -h|--help)
                show_help
                exit 0
                ;;
            *)
                log_error "알 수 없는 옵션: $1"
                log_info "사용법 정보는 --help를 사용하세요."
                exit 1
                ;;
        esac
    done
}

# --- 시스템 요구 사항 유효성 검사 ---
validate_system_requirements() {
    log_step "시스템 요구 사항 유효성 검사 중..."
    
    # 요청 시 유효성 검사 건너뛰기
    if [[ "${SKIP_VALIDATION:-false}" == "true" ]]; then
        log_warn "환경 유효성 검사 건너뛰기 (SKIP_VALIDATION=true)"
        return 0
    fi
    
    # 기본 환경 유효성 검사 (common.sh)
    validate_environment
    
    # 추가 개발 도구 확인
    local dev_tools=("git" "unzip" "curl")
    for tool in "${dev_tools[@]}"; do
        if ! command_exists "$tool"; then
            die "필수 개발 도구를 찾을 수 없습니다: $tool" 2
        fi
        log_debug "개발 도구 찾음: $tool"
    done
    
    # Git LFS 확인
    if ! command_exists "git-lfs"; then
        die "Git LFS를 찾을 수 없습니다. Git LFS를 설치해주세요: https://git-lfs.github.io/" 2
    fi
    log_debug "Git LFS 찾음"
    
    # Git 사용자 설정 확인
    if ! git config user.name >/dev/null 2>&1; then
        log_warn "Git user.name이 설정되지 않았습니다. 'git config --global user.name 'Your Name''을 실행하세요."
    fi
    
    if ! git config user.email >/dev/null 2>&1; then
        log_warn "Git user.email이 설정되지 않았습니다. 'git config --global user.email 'your.email@example.com''을 실행하세요."
    fi
    
    # 사용 가능한 디스크 공간 확인 (최소 2GB)
    local available_space
    if is_macos; then
        available_space=$(df -g "$PROJECT_ROOT" | awk 'NR==2 {print $4}') # macOS
    else
        available_space=$(df -BG "$PROJECT_ROOT" | awk 'NR==2 {print $4}' | sed 's/G//') # Linux
    fi
    
    if [[ "$available_space" -lt 2 ]]; then
        log_warn "디스크 공간 부족: ${available_space}GB 사용 가능. 최소 2GB 권장."
    fi
    
    log_success "시스템 요구 사항 유효성 검사 완료"
}

# --- Git LFS 설정 ---
setup_git_lfs() {
    log_step "Git LFS 설정 중..."
    
    cd "$PROJECT_ROOT"
    
    # Git LFS가 이미 초기화되었는지 확인
    if git lfs env >/dev/null 2>&1; then
        log_debug "Git LFS 환경이 이미 구성되었습니다."
    else
        log_info "Git LFS 초기화 중..."
        execute_cmd "git lfs install" "Git LFS 설치"
    fi
    
    # .gitattributes 파일이 존재하고 LFS 항목이 있는지 확인
    if [[ -f ".gitattributes" ]]; then
        local lfs_files
        lfs_files=$(grep -c "filter=lfs" .gitattributes 2>/dev/null || echo "0")
        if [[ "$lfs_files" -gt 0 ]]; then
            log_info ".gitattributes에서 $lfs_files개의 LFS 파일 패턴 찾음"
            
            # LFS 파일 풀 (다운로드)
            log_info "LFS 파일 풀링 중..."
            execute_cmd "git lfs pull" "Git LFS 풀"
            
            # LFS 파일 확인
            log_info "LFS 파일 확인 중..."
            if git lfs ls-files >/dev/null 2>&1; then
                local lfs_file_count
                lfs_file_count=$(git lfs ls-files | wc -l | tr -d ' ')
                if [[ "$lfs_file_count" -gt 0 ]]; then
                    log_success "$lfs_file_count개의 LFS 파일 성공적으로 풀링됨"
                    git lfs ls-files | while read -r line; do
                        log_debug "LFS 파일: $line"
                    done
                else
                    log_warn "저장소에서 LFS 파일을 찾을 수 없습니다."
                fi
            fi
        else
            log_info ".gitattributes에서 LFS 파일 패턴을 찾을 수 없습니다."
        fi
    else
        log_warn ".gitattributes 파일을 찾을 수 없어 LFS 파일 풀링 건너뛰기"
    fi
    
    log_success "Git LFS 설정 완료"
}

# --- Git 서브모듈 설정 ---
setup_submodules() {
    if [[ "$SKIP_SUBMODULES" == "true" ]]; then
        log_info "Git 서브모듈 초기화 건너뛰기"
        return 0
    fi
    
    log_step "Git 서브모듈 설정 중..."
    
    cd "$PROJECT_ROOT"
    
    # .gitmodules 파일이 존재하는지 확인
    if [[ ! -f ".gitmodules" ]]; then
        log_warn ".gitmodules 파일을 찾을 수 없어 서브모듈 설정 건너뛰기"
        return 0
    fi
    
    # 서브모듈 초기화 및 업데이트
    if [[ "$FORCE_REINSTALL" == "true" ]]; then
        log_info "서브모듈 강제 재설치 중..."
        execute_cmd "git submodule deinit --all -f" "서브모듈 deinit"
    fi
    
    execute_cmd "git submodule init" "서브모듈 init"
    execute_cmd "git submodule update --recursive" "서브모듈 update"
    
    # 지정된 브랜치로 전환 (VSCode 서브모듈)
    local vscode_dir="$PROJECT_ROOT/$VSCODE_SUBMODULE_PATH"
    if [[ -d "$vscode_dir" ]]; then
        cd "$vscode_dir"
        if git show-ref --verify --quiet "refs/heads/$VSCODE_BRANCH"; then
            execute_cmd "git checkout $VSCODE_BRANCH" "$VSCODE_BRANCH 브랜치 체크아웃"
        elif git show-ref --verify --quiet "refs/remotes/origin/$VSCODE_BRANCH"; then
            execute_cmd "git checkout -b $VSCODE_BRANCH origin/$VSCODE_BRANCH" "$VSCODE_BRANCH 브랜치 체크아웃"
        else
            log_warn "브랜치 $VSCODE_BRANCH를 찾을 수 없습니다. 현재 브랜치 유지"
        fi
    fi
    
    # 지정된 브랜치로 전환 (Athena 서브모듈)
    local athena_dir="$PROJECT_ROOT/$ATHENA_SUBMODULE_PATH"
    if [[ -d "$athena_dir" ]]; then
        cd "$athena_dir"
        if git show-ref --verify --quiet "refs/heads/$VSCODE_BRANCH"; then
            execute_cmd "git checkout $VSCODE_BRANCH" "$VSCODE_BRANCH 브랜치 체크아웃"
        elif git show-ref --verify --quiet "refs/remotes/origin/$VSCODE_BRANCH"; then
            execute_cmd "git checkout -b $VSCODE_BRANCH origin/$VSCODE_BRANCH" "$VSCODE_BRANCH 브랜치 체크아웃"
        else
            log_warn "브랜치 $VSCODE_BRANCH를 찾을 수 없습니다. 현재 브랜치 유지"
        fi
    fi

    log_success "Git 서브모듈 설정 완료"
}

# --- 프로젝트 의존성 설치 ---
install_dependencies() {
    if [[ "$SKIP_DEPENDENCIES" == "true" ]]; then
        log_info "의존성 설치 건너뛰기"
        return 0
    fi
    
    log_step "프로젝트 의존성 설치 중..."
    
    # Extension Host 의존성 설치
    if [[ -d "$PROJECT_ROOT/$EXTENSION_HOST_DIR" && -f "$PROJECT_ROOT/$EXTENSION_HOST_DIR/package.json" ]]; then
        log_info "Extension Host 의존성 설치 중..."
        cd "$PROJECT_ROOT/$EXTENSION_HOST_DIR"
        
        if [[ "$FORCE_REINSTALL" == "true" ]]; then
            remove_dir "node_modules"
            [[ -f "package-lock.json" ]] && rm -f "package-lock.json"
        fi
        
        execute_cmd "npm install" "Extension Host 의존성 설치"
    fi
    
    # VSCode 확장 의존성 설치
    local vscode_dir="$PROJECT_ROOT/$PLUGIN_SUBMODULE_PATH" # PLUGIN_SUBMODULE_PATH는 Athena를 가리킴
    if [[ -d "$vscode_dir" && -f "$vscode_dir/package.json" ]]; then
        log_info "VSCode 확장 의존성 설치 중..."
        cd "$vscode_dir"
        
        if [[ "$FORCE_REINSTALL" == "true" ]]; then
            remove_dir "node_modules"
            [[ -f "package-lock.json" ]] && rm -f "package-lock.json"
            [[ -f "pnpm-lock.yaml" ]] && rm -f "pnpm-lock.yaml"
        fi
        
        # pnpm이 사용 가능하고 lock 파일이 있으면 pnpm 사용
        local pkg_manager="npm"
        if command_exists "pnpm" && [[ -f "pnpm-lock.yaml" ]]; then
            pkg_manager="pnpm"
        fi
        
        execute_cmd "$pkg_manager install" "VSCode 확장 의존성 설치"
    fi
    
    # Athena 확장 의존성 설치 (위의 VSCode 확장과 동일한 경로를 사용하므로 중복될 수 있음)
    local athena_dir="$PROJECT_ROOT/$ATHENA_SUBMODULE_PATH"
    if [[ -d "$athena_dir" && -f "$athena_dir/package.json" ]]; then
        log_info "Athena 확장 의존성 설치 중..."
        cd "$athena_dir"

        if [[ "$FORCE_REINSTALL" == "true" ]]; then
            remove_dir "node_modules"
            [[ -f "package-lock.json" ]] && rm -f "package-lock.json"
            [[ -f "pnpm-lock.yaml" ]] && rm -f "pnpm-lock.yaml"
        fi

        local pkg_manager="npm"
        if command_exists "pnpm" && [[ -f "pnpm-lock.yaml" ]]; then
            pkg_manager="pnpm"
        fi

        execute_cmd "$pkg_manager install" "Athena 확장 의존성 설치"
    fi

    log_success "의존성 설치 완료"
}

# --- 프로젝트 패치 적용 및 VSCode 파일 복사 ---
apply_patches() {
    if [[ "$APPLY_PATCHES" != "true" ]]; then
        log_info "패치 적용 건너뛰기"
        return 0
    fi
    
    log_step "프로젝트 패치 적용 중..."
    
    local patch_file="$PROJECT_ROOT/$PATCH_FILE"
    if [[ ! -f "$patch_file" ]]; then
        log_warn "패치 파일을 찾을 수 없습니다: $patch_file"
        return 0
    fi
    
    # deps/vscode를 소스 디렉터리로 사용
    local vscode_source_dir="$PROJECT_ROOT/deps/vscode"
    local vscode_target_dir="$PROJECT_ROOT/$EXTENSION_HOST_DIR/vscode"
    
    if [[ ! -d "$vscode_source_dir" ]]; then
        log_warn "VSCode 소스 디렉터리를 찾을 수 없습니다: $vscode_source_dir"
        return 0
    fi
    
    cd "$vscode_source_dir"
    execute_cmd "git clean -dfx && git reset --hard" "VSCode 소스 초기화"
 
    # 패치를 적용할 수 있는지 확인
    if git apply --check "$patch_file" 2>/dev/null; then
        log_info "VSCode 소스에 패치 적용 중..."
        execute_cmd "git apply '$patch_file'" "패치 적용"
        
        # src/* 내용을 대상 디렉터리로 복사
        log_info "src/*를 '$vscode_target_dir'로 복사 중..."
        if [[ ! -d "$vscode_target_dir" ]]; then
            ensure_dir "$vscode_target_dir"
        else
            rm -rf "$vscode_target_dir"/*
        fi
        
        if [[ -d "$vscode_source_dir/src" ]]; then
            execute_cmd "cp -r src/* '$vscode_target_dir/'" "VSCode 파일 복사"
        else
            log_error "VSCode src 디렉터리를 찾을 수 없습니다: $vscode_source_dir"
            exit 4
        fi
        
        # 소스 저장소 초기화
        log_info "VSCode 소스 저장소 초기화 중..."
        execute_cmd "git reset --hard" "git reset"
        execute_cmd "git clean -fd" "git clean"
        
        log_success "패치 적용 및 VSCode 파일 복사 완료"
    else
        # 패치가 이미 적용되었는지 확인
        if git apply --reverse --check "$patch_file" 2>/dev/null; then
            log_info "패치가 이미 적용된 것으로 보입니다. 파일 복사 중..."
            
            # 패치가 이미 적용되었더라도 파일을 복사합니다.
            if [[ ! -d "$vscode_target_dir" ]]; then
                ensure_dir "$vscode_target_dir"
            else
                rm -rf "$vscode_target_dir"/*
            fi
            
            if [[ -d "$vscode_source_dir/src" ]]; then
                execute_cmd "cp -r src/* '$vscode_target_dir/'" "VSCode 파일 복사"
                log_success "VSCode 파일 복사 완료"
            else
                log_error "VSCode src 디렉터리를 찾을 수 없습니다: $vscode_source_dir"
                exit 4
            fi
        else
            log_error "패치를 적용할 수 없습니다 (충돌이 있을 수 있음)"
            log_info "수동으로 충돌을 해결해야 할 수 있습니다."
            exit 4
        fi
    fi
}

# --- 개발 환경 설정 ---
setup_dev_environment() {
    log_step "개발 환경 설정 중..."
    
    # 필요한 디렉터리 생성
    local dirs_to_create=(
        "$PROJECT_ROOT/logs"
        "$PROJECT_ROOT/tmp"
        "$PROJECT_ROOT/build"
    )
    
    for dir in "${dirs_to_create[@]}"; do
        ensure_dir "$dir"
    done
    
    # Git 훅 설정 (존재하는 경우)
    local hooks_dir="$PROJECT_ROOT/.githooks"
    if [[ -d "$hooks_dir" ]]; then
        log_info "Git 훅 설정 중..."
        execute_cmd "git config core.hooksPath .githooks" "Git 훅 설정"
        
        # 훅 파일에 실행 권한 부여
        find "$hooks_dir" -type f -exec chmod +x {} \;
    fi
    
    # 환경 파일 템플릿 생성 (존재하지 않는 경우)
    local env_file="$PROJECT_ROOT/.env.local"
    if [[ ! -f "$env_file" ]]; then
        log_info "환경 파일 템플릿 생성 중..."
        cat > "$env_file" << 'EOF'
# 로컬 환경 설정
# 이 파일을 .env.local로 복사하고 필요에 따라 사용자 정의하세요.

# 빌드 구성
# BUILD_MODE=release
# VERBOSE=false

# 개발 설정
# SKIP_VALIDATION=false
# USE_DEBUG_BUILD=false

# 경로 (일반적으로 자동 감지됨)
# PROJECT_ROOT=
# VSCODE_DIR=
EOF
        log_info "$env_file 생성됨 - 필요에 따라 사용자 정의하세요."
    fi
    
    log_success "개발 환경 설정 완료"
}

# --- 설정 확인 ---
verify_setup() {
    log_step "설정 확인 중..."
    
    local errors=0
    
    # 중요 디렉터리 확인
    local critical_dirs=(
        "$PROJECT_ROOT/$EXTENSION_HOST_DIR"
        "$PROJECT_ROOT/$IDEA_DIR"
    )
    
    for dir in "${critical_dirs[@]}"; do
        if [[ ! -d "$dir" ]]; then
            log_error "중요 디렉터리 누락: $dir"
            ((errors++))
        fi
    done
    
    # VSCode 서브모듈 확인
    local vscode_dir="$PROJECT_ROOT/$VSCODE_SUBMODULE_PATH"
    if [[ ! -d "$vscode_dir" ]] || [[ ! "$(ls -A "$vscode_dir" 2>/dev/null)" ]]; then
        log_error "VSCode 서브모듈이 제대로 초기화되지 않았습니다: $vscode_dir"
        ((errors++))
    fi
    
    # package.json 파일 확인
    local package_files=(
        "$PROJECT_ROOT/$EXTENSION_HOST_DIR/package.json"
    )
    
    for file in "${package_files[@]}"; do
        if [[ ! -f "$file" ]]; then
            log_error "패키지 파일 누락: $file"
            ((errors++))
        fi
    done
    
    # 빌드 도구 확인
    if [[ -d "$PROJECT_ROOT/$IDEA_DIR" ]]; then
        if [[ ! -f "$PROJECT_ROOT/$IDEA_DIR/build.gradle" && ! -f "$PROJECT_ROOT/$IDEA_DIR/build.gradle.kts" ]]; then
            log_warn "IDEA 디렉터리에서 Gradle 빌드 파일을 찾을 수 없습니다."
        fi
    fi
    
    if [[ $errors -gt 0 ]]; then
        log_error "설정 확인 실패 ($errors개 오류 발생)"
        exit 3
    fi
    
    log_success "설정 확인 통과"
}

# --- 메인 설정 함수 ---
main() {
    log_info "RunVSAgent 개발 환경 설정 시작..."
    log_info "스크립트: $SCRIPT_NAME v$SCRIPT_VERSION"
    log_info "플랫폼: $(get_platform)"
    log_info "프로젝트 루트: $PROJECT_ROOT"
    
    if [[ "$DRY_RUN" == "true" ]]; then
        log_warn "DRY RUN 모드 - 변경 사항이 적용되지 않습니다."
    fi
    
    # 인자 파싱
    parse_setup_args "$@"
    
    # 설정 단계 실행
    validate_system_requirements
    setup_git_lfs
    setup_submodules
    install_dependencies
    apply_patches
    setup_dev_environment
    verify_setup
    
    log_success "설정 성공적으로 완료되었습니다!"
    log_info ""
    log_info "다음 단계:"
    log_info "  1. 프로젝트를 빌드하려면 './scripts/build.sh'를 실행하세요."
    log_info "  2. 빌드 옵션을 보려면 './scripts/build.sh --help'를 실행하세요."
    log_info "  3. idea/ 디렉터리에서 생성된 파일을 확인하세요."
    log_info ""
    log_info "자세한 내용은 프로젝트 문서를 참조하세요."
}

# 모든 인자와 함께 메인 함수 실행
main "$@"
