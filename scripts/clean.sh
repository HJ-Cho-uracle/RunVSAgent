#!/bin/bash

# RunVSAgent 프로젝트 정리 스크립트
# 이 스크립트는 빌드 결과물 및 임시 파일들을 정리합니다.

# 오류 발생 시 즉시 종료, 정의되지 않은 변수 사용 시 오류, 파이프라인 오류 시 오류
set -euo pipefail

# 공통 유틸리티 스크립트 로드
source "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/lib/common.sh"
source "$SCRIPT_DIR/lib/build.sh" # 빌드 관련 정리 함수 (clean_build)를 사용하기 위해 로드

# --- 스크립트 설정 ---
readonly SCRIPT_NAME="clean.sh"
readonly SCRIPT_VERSION="1.0.0"

# --- 정리 대상 정의 ---
readonly TARGET_ALL="all"       # 모든 것을 정리
readonly TARGET_BUILD="build"   # 빌드 결과물만 정리
readonly TARGET_DEPS="deps"     # 의존성만 정리 (node_modules, lock 파일 등)
readonly TARGET_CACHE="cache"   # 캐시 파일만 정리 (npm, gradle 캐시 등)
readonly TARGET_LOGS="logs"     # 로그 파일만 정리
readonly TARGET_TEMP="temp"     # 임시 파일만 정리

# --- 정리 구성 변수 ---
CLEAN_TARGET="$TARGET_BUILD"    # 기본 정리 대상은 'build'
FORCE_CLEAN=false               # 확인 메시지 없이 강제 정리할지 여부
KEEP_LOGS=false                 # 로그 파일을 유지할지 여부

# --- 스크립트 도움말 표시 ---
show_help() {
    cat << EOF
$SCRIPT_NAME - RunVSAgent 프로젝트 결과물 정리

사용법:
    $SCRIPT_NAME [옵션] [대상]

설명:
    이 스크립트는 다양한 유형의 프로젝트 결과물을 정리합니다:
    - 빌드 결과물 (컴파일된 파일, 배포판)
    - 의존성 (node_modules, 패키지 잠금 파일)
    - 캐시 파일 (npm, gradle, 임시 파일)
    - 로그 파일
    - 임시 파일

대상:
    build       빌드 결과물만 정리 (기본값)
    deps        의존성 정리 (node_modules, 잠금 파일)
    cache       캐시 파일 및 임시 디렉터리 정리
    logs        로그 파일 정리
    temp        임시 파일 정리
    all         모든 것을 정리

옵션:
    -f, --force           확인 메시지 없이 강제 정리
    -k, --keep-logs       정리 시 로그 파일 유지
    -v, --verbose         자세한 출력 활성화
    -n, --dry-run         실행하지 않고 정리될 내용 표시
    -h, --help            이 도움말 메시지 표시

예시:
    $SCRIPT_NAME                    # 빌드 결과물 정리
    $SCRIPT_NAME all                # 모든 것을 정리
    $SCRIPT_NAME --force deps       # 의존성 강제 정리
    $SCRIPT_NAME --dry-run all      # 정리될 내용 미리보기

안전:
    - 기본적으로 빌드 결과물만 정리됩니다.
    - 확인 메시지를 건너뛰려면 --force를 사용하세요.
    - 정리될 내용을 미리 보려면 --dry-run을 사용하세요.
    - --keep-logs=false로 지정하지 않는 한 로그 파일은 보존됩니다.

종료 코드:
    0    성공
    1    일반 오류
    2    사용자 취소
    3    잘못된 인자

EOF
}

# --- 명령줄 인자 파싱 ---
parse_clean_args() {
    while [[ $# -gt 0 ]]; do
        case $1 in
            -f|--force)
                FORCE_CLEAN=true
                shift
                ;;
            -k|--keep-logs)
                KEEP_LOGS=true
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
            -*)
                log_error "알 수 없는 옵션: $1"
                log_info "사용법 정보는 --help를 사용하세요."
                exit 3
                ;;
            *)
                # 위치 인자 (대상)
                CLEAN_TARGET="$1"
                shift
                ;;
        esac
    done
    
    # 정리 대상 유효성 검사
    case "$CLEAN_TARGET" in
        "$TARGET_ALL"|"$TARGET_BUILD"|"$TARGET_DEPS"|"$TARGET_CACHE"|"$TARGET_LOGS"|"$TARGET_TEMP")
            ;;
        *)
            log_error "유효하지 않은 정리 대상: $CLEAN_TARGET"
            log_info "유효한 대상: $TARGET_ALL, $TARGET_BUILD, $TARGET_DEPS, $TARGET_CACHE, $TARGET_LOGS, $TARGET_TEMP"
            exit 3
            ;;
    esac
}

# --- 정리 작업 확인 ---
confirm_clean() {
    if [[ "$FORCE_CLEAN" == "true" || "$DRY_RUN" == "true" ]]; then
        return 0
    fi
    
    echo ""
    log_warn "다음 대상을 정리합니다: $CLEAN_TARGET"
    
    case "$CLEAN_TARGET" in
        "$TARGET_ALL")
            log_warn "  - 모든 빌드 결과물"
            log_warn "  - 모든 의존성 (node_modules)"
            log_warn "  - 모든 캐시 파일"
            log_warn "  - 모든 임시 파일"
            [[ "$KEEP_LOGS" != "true" ]] && log_warn "  - 모든 로그 파일"
            ;;
        "$TARGET_BUILD")
            log_warn "  - VSCode 확장 빌드 파일"
            log_warn "  - 기본 확장 빌드 파일"
            log_warn "  - IDEA 플러그인 빌드 파일"
            log_warn "  - 생성된 VSIX 파일"
            ;;
        "$TARGET_DEPS")
            log_warn "  - 모든 node_modules 디렉터리"
            log_warn "  - 패키지 잠금 파일"
            log_warn "  - Gradle 캐시"
            ;;
        "$TARGET_CACHE")
            log_warn "  - npm 캐시"
            log_warn "  - Gradle 캐시"
            log_warn "  - 임시 빌드 파일"
            ;;
        "$TARGET_LOGS")
            log_warn "  - 모든 로그 파일"
            ;;
        "$TARGET_TEMP")
            log_warn "  - 임시 디렉터리"
            log_warn "  - 빌드 임시 파일"
            ;;
    esac
    
    echo ""
    read -p "계속하시겠습니까? [y/N] " -n 1 -r # 사용자에게 확인 메시지 표시
    echo ""
    
    if [[ ! $REPLY =~ ^[Yy]$ ]]; then
        log_info "정리 작업이 취소되었습니다."
        exit 2
    fi
}

# --- 빌드 결과물 정리 ---
clean_build_artifacts() {
    if [[ "$CLEAN_TARGET" != "$TARGET_BUILD" && "$CLEAN_TARGET" != "$TARGET_ALL" ]]; then
        return 0
    fi
    
    log_step "빌드 결과물 정리 중..."
    
    # VSCode 확장 빌드 정리
    local vscode_dir="$PROJECT_ROOT/$VSCODE_SUBMODULE_PATH"
    if [[ -d "$vscode_dir" ]]; then
        log_info "VSCode 확장 빌드 정리 중..."
        remove_dir "$vscode_dir/bin"
        remove_dir "$vscode_dir/src/dist"
        remove_dir "$vscode_dir/src/webview-ui/build"
        remove_dir "$vscode_dir/out"
        
        # 루트에 있는 .vsix 파일 정리
        find "$vscode_dir" -maxdepth 1 -name "*.vsix" -type f -exec rm -f {} \; 2>/dev/null || true
    fi
    
    # 기본 확장 빌드 정리
    local base_dir="$PROJECT_ROOT/$EXTENSION_HOST_DIR"
    if [[ -d "$base_dir" ]]; then
        log_info "기본 확장 빌드 정리 중..."
        remove_dir "$base_dir/dist"
        remove_dir "$base_dir/out"
    fi
    
    # IDEA 플러그인 빌드 정리
    local idea_dir="$PROJECT_ROOT/$IDEA_DIR"
    if [[ -d "$idea_dir" ]]; then
        log_info "IDEA 플러그인 빌드 정리 중..."
        remove_dir "$idea_dir/build"
        remove_dir "$idea_dir/roo-code" # 이전 roo-code 관련 디렉터리 정리
        [[ -f "$idea_dir/prodDep.txt" ]] && rm -f "$idea_dir/prodDep.txt"
    fi
    
    # 디버그 리소스 정리
    remove_dir "$PROJECT_ROOT/debug-resources"
    
    # 프로젝트 루트에 있는 빌드 디렉터리 정리
    remove_dir "$PROJECT_ROOT/build"
    remove_dir "$PROJECT_ROOT/dist"
    
    log_success "빌드 결과물 정리 완료"
}

# --- 의존성 정리 ---
clean_dependencies() {
    if [[ "$CLEAN_TARGET" != "$TARGET_DEPS" && "$CLEAN_TARGET" != "$TARGET_ALL" ]]; then
        return 0
    fi
    
    log_step "의존성 정리 중..."
    
    # 기본 의존성 정리
    local base_dir="$PROJECT_ROOT/$EXTENSION_HOST_DIR"
    if [[ -d "$base_dir" ]]; then
        log_info "기본 의존성 정리 중..."
        remove_dir "$base_dir/node_modules"
        [[ -f "$base_dir/package-lock.json" ]] && rm -f "$base_dir/package-lock.json"
        [[ -f "$base_dir/pnpm-lock.yaml" ]] && rm -f "$base_dir/pnpm-lock.yaml"
    fi
    
    # VSCode 확장 의존성 정리
    local vscode_dir="$PROJECT_ROOT/$VSCODE_SUBMODULE_PATH"
    if [[ -d "$vscode_dir" ]]; then
        log_info "VSCode 확장 의존성 정리 중..."
        remove_dir "$vscode_dir/node_modules"
        [[ -f "$vscode_dir/package-lock.json" ]] && rm -f "$vscode_dir/package-lock.json"
        [[ -f "$vscode_dir/pnpm-lock.yaml" ]] && rm -f "$vscode_dir/pnpm-lock.yaml"
    fi
    
    # Gradle 캐시 정리
    local idea_dir="$PROJECT_ROOT/$IDEA_DIR"
    if [[ -d "$idea_dir" ]]; then
        log_info "Gradle 캐시 정리 중..."
        remove_dir "$idea_dir/.gradle"
        remove_dir "$idea_dir/build" # build 디렉터리는 clean_build_artifacts에서도 정리되지만, 여기서도 안전하게 정리
    fi
    
    # 전역 Gradle 캐시 정리 (사용자 홈 디렉터리)
    if [[ -d "$HOME/.gradle/caches" ]]; then
        log_info "전역 Gradle 캐시 정리 중..."
        if [[ "$DRY_RUN" == "true" ]]; then
            log_debug "다음 내용을 정리할 예정: $HOME/.gradle/caches"
        else
            find "$HOME/.gradle/caches" -type f -name "*.lock" -delete 2>/dev/null || true
            find "$HOME/.gradle/caches" -type d -name "tmp" -exec rm -rf {} + 2>/dev/null || true
        fi
    fi
    
    log_success "의존성 정리 완료"
}

# --- 캐시 파일 정리 ---
clean_cache() {
    if [[ "$CLEAN_TARGET" != "$TARGET_CACHE" && "$CLEAN_TARGET" != "$TARGET_ALL" ]]; then
        return 0
    fi
    
    log_step "캐시 파일 정리 중..."
    
    # npm 캐시 정리
    if command_exists "npm"; then
        log_info "npm 캐시 정리 중..."
        if [[ "$DRY_RUN" == "true" ]]; then
            log_debug "다음 명령어를 실행할 예정: npm cache clean --force"
        else
            npm cache clean --force 2>/dev/null || true
        fi
    fi
    
    # pnpm 캐시 정리
    if command_exists "pnpm"; then
        log_info "pnpm 캐시 정리 중..."
        if [[ "$DRY_RUN" == "true" ]]; then
            log_debug "다음 명령어를 실행할 예정: pnpm store prune"
        else
            pnpm store prune 2>/dev/null || true
        fi
    fi
    
    # 시스템 임시 디렉터리 정리
    log_info "임시 빌드 파일 정리 중..."
    find /tmp -name "${TEMP_PREFIX}*" -type d -exec rm -rf {} + 2>/dev/null || true
    
    # 프로젝트 임시 디렉터리 정리
    remove_dir "$PROJECT_ROOT/tmp"
    
    # OS별 캐시 디렉터리 정리
    case "$(get_platform)" in
        "macos")
            [[ -d "$HOME/Library/Caches/npm" ]] && remove_dir "$HOME/Library/Caches/npm"
            ;;
        "linux")
            [[ -d "$HOME/.cache/npm" ]] && remove_dir "$HOME/.cache/npm"
            ;;
        "windows")
            [[ -d "$HOME/AppData/Local/npm-cache" ]] && remove_dir "$HOME/AppData/Local/npm-cache"
            ;;
    esac
    
    log_success "캐시 파일 정리 완료"
}

# --- 로그 파일 정리 ---
clean_logs() {
    if [[ "$CLEAN_TARGET" != "$TARGET_LOGS" && "$CLEAN_TARGET" != "$TARGET_ALL" ]]; then
        return 0
    fi
    
    if [[ "$KEEP_LOGS" == "true" ]]; then
        log_info "로그 파일 유지 (--keep-logs 지정됨)"
        return 0
    fi
    
    log_step "로그 파일 정리 중..."
    
    # 프로젝트 로그 디렉터리 정리
    if [[ -d "$PROJECT_ROOT/logs" ]]; then
        log_info "프로젝트 로그 정리 중..."
        find "$PROJECT_ROOT/logs" -name "*.log" -type f -exec rm -f {} \; 2>/dev/null || true
        find "$PROJECT_ROOT/logs" -name "*.log.*" -type f -exec rm -f {} \; 2>/dev/null || true
    fi
    
    # npm 디버그 로그 정리
    find "$PROJECT_ROOT" -name "npm-debug.log*" -type f -exec rm -f {} \; 2>/dev/null || true
    find "$PROJECT_ROOT" -name ".npm/_logs" -type d -exec rm -rf {} \; 2>/dev/null || true
    
    # Gradle 로그 정리
    local idea_dir="$PROJECT_ROOT/$IDEA_DIR"
    if [[ -d "$idea_dir" ]]; then
        find "$idea_dir" -name "*.log" -type f -exec rm -f {} \; 2>/dev/null || true
    fi
    
    log_success "로그 파일 정리 완료"
}

# --- 임시 파일 정리 ---
clean_temp() {
    if [[ "$CLEAN_TARGET" != "$TARGET_TEMP" && "$CLEAN_TARGET" != "$TARGET_ALL" ]]; then
        return 0
    fi
    
    log_step "임시 파일 정리 중..."
    
    # 프로젝트 임시 파일 정리
    find "$PROJECT_ROOT" -name ".DS_Store" -type f -exec rm -f {} \; 2>/dev/null || true
    find "$PROJECT_ROOT" -name "Thumbs.db" -type f -exec rm -f {} \; 2>/dev/null || true
    find "$PROJECT_ROOT" -name "*.tmp" -type f -exec rm -f {} \; 2>/dev/null || true
    find "$PROJECT_ROOT" -name "*.temp" -type f -exec rm -f {} \; 2>/dev/null || true
    
    # 에디터 임시 파일 정리
    find "$PROJECT_ROOT" -name "*~" -type f -exec rm -f {} \; 2>/dev/null || true
    find "$PROJECT_ROOT" -name "*.swp" -type f -exec rm -f {} \; 2>/dev/null || true
    find "$PROJECT_ROOT" -name "*.swo" -type f -exec rm -f {} \; 2>/dev/null || true
    
    # 시스템 임시 디렉터리 정리
    find /tmp -name "${TEMP_PREFIX}*" -type d -exec rm -rf {} + 2>/dev/null || true
    
    log_success "임시 파일 정리 완료"
}

# --- 정리 요약 표시 ---
show_clean_summary() {
    log_step "정리 요약"
    
    echo ""
    log_success "정리 작업이 완료되었습니다!"
    log_info "대상: $CLEAN_TARGET"
    log_info "플랫폼: $(get_platform)"
    
    if [[ "$DRY_RUN" == "true" ]]; then
        log_info "모드: DRY RUN (실제로 삭제된 파일 없음)"
    fi
    
    echo ""
    log_info "정리된 내용:"
    
    case "$CLEAN_TARGET" in
        "$TARGET_ALL")
            log_info "  ✓ 빌드 결과물"
            log_info "  ✓ 의존성"
            log_info "  ✓ 캐시 파일"
            log_info "  ✓ 임시 파일"
            [[ "$KEEP_LOGS" != "true" ]] && log_info "  ✓ 로그 파일"
            ;;
        "$TARGET_BUILD")
            log_info "  ✓ 빌드 결과물"
            ;;
        "$TARGET_DEPS")
            log_info "  ✓ 의존성"
            ;;
        "$TARGET_CACHE")
            log_info "  ✓ 캐시 파일"
            ;;
        "$TARGET_LOGS")
            log_info "  ✓ 로그 파일"
            ;;
        "$TARGET_TEMP")
            log_info "  ✓ 임시 파일"
            ;;
    esac
    
    echo ""
    log_info "다음 단계:"
    if [[ "$CLEAN_TARGET" == "$TARGET_ALL" || "$CLEAN_TARGET" == "$TARGET_DEPS" ]]; then
        log_info "  1. 의존성을 다시 설치하려면 './scripts/setup.sh'를 실행하세요."
        log_info "  2. 프로젝트를 다시 빌드하려면 './scripts/build.sh'를 실행하세요."
    elif [[ "$CLEAN_TARGET" == "$TARGET_BUILD" ]]; then
        log_info "  1. 프로젝트를 다시 빌드하려면 './scripts/build.sh'를 실행하세요."
    fi
    
    echo ""
}

# --- 메인 정리 함수 ---
main() {
    log_info "RunVSAgent 정리 프로세스 시작..."
    log_info "스크립트: $SCRIPT_NAME v$SCRIPT_VERSION"
    log_info "플랫폼: $(get_platform)"
    log_info "프로젝트 루트: $PROJECT_ROOT"
    
    # 인자 파싱
    parse_clean_args "$@"
    
    log_info "정리 구성:"
    log_info "  대상: $CLEAN_TARGET"
    log_info "  강제: $FORCE_CLEAN"
    log_info "  로그 유지: $KEEP_LOGS"
    
    if [[ "$DRY_RUN" == "true" ]]; then
        log_warn "DRY RUN 모드 - 파일이 실제로 삭제되지 않습니다."
    fi
    
    # 작업 확인
    confirm_clean
    
    # 정리 작업 실행
    clean_build_artifacts
    clean_dependencies
    clean_cache
    clean_logs
    clean_temp
    
    # 요약 표시
    show_clean_summary
    
    log_success "정리 프로세스 성공적으로 완료되었습니다!"
}

# 모든 인자와 함께 메인 함수 실행
main "$@"
