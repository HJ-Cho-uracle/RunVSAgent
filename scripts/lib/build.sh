#!/bin/bash

# 빌드 관련 유틸리티 함수
# 이 파일은 VSCode 확장 및 IDEA 플러그인 빌드를 위한 함수를 제공합니다.

# --- 공통 유틸리티 로드 ---
# common.sh 스크립트가 동일한 디렉터리에 있다고 가정합니다.
LIB_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$LIB_DIR/common.sh"

# --- 빌드 구성 ---
readonly DEFAULT_BUILD_MODE="release" # 기본 빌드 모드
readonly VSCODE_BRANCH="develop"      # VSCode 서브모듈의 브랜치
readonly IDEA_DIR="jetbrains_plugin"  # IDEA 플러그인 디렉터리
readonly TEMP_PREFIX="build_temp_"    # 임시 디렉터리 접두사

# --- 빌드 모드 ---
readonly BUILD_MODE_RELEASE="release" # 릴리스 모드
readonly BUILD_MODE_DEBUG="debug"     # 디버그 모드

# --- 전역 빌드 변수 ---
BUILD_MODE="$DEFAULT_BUILD_MODE" # 현재 빌드 모드
VSIX_FILE=""                     # 생성된 VSIX 파일 경로
SKIP_VSCODE_BUILD=false          # VSCode 빌드 건너뛰기 여부
SKIP_BASE_BUILD=false            # 기본 확장 빌드 건너뛰기 여부
SKIP_IDEA_BUILD=false            # IDEA 빌드 건너뛰기 여부

# --- 빌드 환경 초기화 ---
init_build_env() {
    log_step "빌드 환경 초기화 중..."
    
    # 빌드 경로 설정
    export BUILD_TEMP_DIR="$(mktemp -d -t ${TEMP_PREFIX}XXXXXX)" # 임시 빌드 디렉터리 생성
    # 빌드할 메인 플러그인은 Athena. ATHENA_SUBMODULE_PATH는 common.sh에 정의되어 있다고 가정합니다.
    export PLUGIN_BUILD_DIR="$PROJECT_ROOT/$ATHENA_SUBMODULE_PATH"
    export BASE_BUILD_DIR="$PROJECT_ROOT/$EXTENSION_HOST_DIR"
    export IDEA_BUILD_DIR="$PROJECT_ROOT/$IDEA_DIR"
    export VSCODE_PLUGIN_NAME="athena" # 빌드할 VSCode 플러그인 이름
    export VSCODE_PLUGIN_TARGET_DIR="$IDEA_BUILD_DIR/plugins/${VSCODE_PLUGIN_NAME}" # IDEA 플러그인 내 대상 디렉터리

    # 빌드 도구 유효성 검사
    validate_build_tools
    
    log_debug "임시 빌드 디렉터리: $BUILD_TEMP_DIR"
    log_debug "플러그인 빌드 디렉터리: $PLUGIN_BUILD_DIR"
    log_debug "기본 빌드 디렉터리: $BASE_BUILD_DIR"
    log_debug "IDEA 빌드 디렉터리: $IDEA_BUILD_DIR"
    
    log_success "빌드 환경 초기화 완료"
}

# --- 빌드 도구 유효성 검사 ---
validate_build_tools() {
    log_step "빌드 도구 유효성 검사 중..."
    
    local required_tools=("git" "node" "npm" "unzip") # 필수 도구 목록
    
    # pnpm 패키지 관리자 확인
    if command_exists "pnpm"; then
        log_debug "pnpm 패키지 관리자 찾음"
    else
        log_warn "pnpm을 찾을 수 없습니다. npm을 사용합니다."
    fi
    
    # Gradle 확인 (IDEA 플러그인용)
    if command_exists "gradle" || [[ -f "$IDEA_BUILD_DIR/gradlew" ]]; then
        log_debug "Gradle 빌드 도구 찾음"
    else
        log_warn "Gradle을 찾을 수 없습니다. IDEA 플러그인 빌드가 실패할 수 있습니다."
    fi
    
    # 모든 필수 도구 확인
    for tool in "${required_tools[@]}"; do
        if ! command_exists "$tool"; then
            die "필수 빌드 도구를 찾을 수 없습니다: $tool"
        fi
        log_debug "빌드 도구 찾음: $tool"
    done
    
    log_success "빌드 도구 유효성 검사 통과"
}

# --- Git 서브모듈 초기화 ---
init_submodules() {
    log_step "Git 서브모듈 초기화 중..."
    
    # 플러그인 빌드 디렉터리가 비어 있으면 초기화
    if [[ ! -d "$PLUGIN_BUILD_DIR" ]] || [[ ! "$(ls -A "$PLUGIN_BUILD_DIR" 2>/dev/null)" ]]; then
        log_info "VSCode 확장 서브모듈을 찾을 수 없거나 비어 있습니다. 초기화 중..."
        
        cd "$PROJECT_ROOT"
        execute_cmd "git submodule init" "git submodule init"
        execute_cmd "git submodule update --recursive" "git submodule update"
        
        log_info "Switching to $VSCODE_BRANCH branch..."
        cd "$PLUGIN_BUILD_DIR"
        execute_cmd "git checkout $VSCODE_BRANCH" "git checkout $VSCODE_BRANCH"
        
        log_success "Git 서브모듈 초기화 완료"
    else
        log_info "VSCode 확장 서브모듈이 이미 존재합니다. 초기화 건너뛰기"
    fi
}

# --- VSCode에 패치 적용 ---
apply_vscode_patches() {
    local patch_file="$1"
    
    if [[ -z "$patch_file" ]] || [[ ! -f "$patch_file" ]]; then
        log_warn "패치 파일이 지정되지 않았거나 파일을 찾을 수 없습니다: $patch_file"
        return 0
    fi
    
    log_step "VSCode 패치 적용 중..."
    
    cd "$PLUGIN_BUILD_DIR"
    
    # 패치가 이미 적용되었는지 확인
    if git apply --check "$patch_file" 2>/dev/null; then
        execute_cmd "git apply '$patch_file'" "패치 적용"
        log_success "패치 성공적으로 적용됨"
    else
        log_warn "패치를 적용할 수 없습니다 (이미 적용되었거나 충돌이 있을 수 있음)"
    fi
}

# --- VSCode 변경 사항 되돌리기 ---
revert_vscode_changes() {
    log_step "VSCode 변경 사항 되돌리기 중..."
    
    cd "$PLUGIN_BUILD_DIR"
    execute_cmd "git reset --hard" "git reset"
    execute_cmd "git clean -fd" "git clean"
    
    log_success "VSCode 변경 사항 되돌리기 완료"
}

# --- VSCode 확장 빌드 ---
build_vscode_extension() {
    if [[ "$SKIP_VSCODE_BUILD" == "true" ]]; then
        log_info "VSCode 확장 빌드 건너뛰기"
        return 0
    fi
    
    log_step "VSCode 확장 빌드 중..."
    
    cd "$PLUGIN_BUILD_DIR"
    
    # 의존성 설치 (pnpm 또는 npm 사용)
    local pkg_manager="npm" # pkg_manager를 여기서 초기화합니다.
    if command_exists "pnpm" && [[ -f "pnpm-lock.yaml" ]]; then
        pkg_manager="pnpm"
    fi
    
    log_info "$pkg_manager 를 사용하여 의존성 설치 중..."
    execute_cmd "$pkg_manager install" "의존성 설치"
    
    # 필요한 경우 Windows 호환성 수정 적용
    apply_windows_compatibility_fix
    
    # 모드에 따라 빌드
    if [[ "$BUILD_MODE" == "$BUILD_MODE_DEBUG" ]]; then
        log_info "디버그 모드로 빌드 중..."
        export USE_DEBUG_BUILD="true"
        if ! "$pkg_manager" run vsix; then
            die "VSIX 빌드 실패"
        fi
        if ! "$pkg_manager" run bundle; then
            die "번들 빌드 실패"
        fi
    else
        log_info "릴리스 모드로 빌드 중..."
        if ! "$pkg_manager" run vsix; then
            die "VSIX 빌드 실패"
        fi
    fi
    
    # 생성된 VSIX 파일 찾기
    VSIX_FILE=$(get_latest_file "$PLUGIN_BUILD_DIR/bin" "*.vsix")
    if [[ -z "$VSIX_FILE" ]]; then
        die "빌드 후 VSIX 파일을 찾을 수 없습니다."
    fi
    
    log_success "VSCode 확장 빌드됨: $VSIX_FILE"
}

# --- Windows 호환성 수정 적용 ---
apply_windows_compatibility_fix() {
    local windows_release_file="$PLUGIN_BUILD_DIR/node_modules/.pnpm/windows-release@6.1.0/node_modules/windows-release/index.js"
    
    if [[ -f "$windows_release_file" ]]; then
        log_debug "Windows 호환성 수정 적용 중..."
        
        # 크로스 플랫폼 호환성을 위해 perl 사용
        if command_exists "perl"; then
            perl -i -pe "s/execaSync\\('wmic', \\['os', 'get', 'Caption'\\]\\)\\.stdout \\|\\| ''/''/g" "$windows_release_file"
            perl -i -pe "s/execaSync\\('powershell', \\['\\(Get-CimInstance -ClassName Win32_OperatingSystem\\)\\.caption'\\]\\)\\.stdout \\|\\| ''/''/g" "$windows_release_file"
            log_debug "Windows 호환성 수정 적용됨"
        else
            log_warn "perl을 찾을 수 없습니다. Windows 호환성 수정 건너뛰기"
        fi
    fi
}

# --- VSIX 파일 압축 해제 ---
extract_vsix() {
    local vsix_file="$1"
    local extract_dir="$2"
    
    if [[ -z "$vsix_file" ]] || [[ ! -f "$vsix_file" ]]; then
        die "VSIX 파일을 찾을 수 없습니다: $vsix_file"
    fi
    
    log_step "VSIX 파일 압축 해제 중..."
    
    ensure_dir "$extract_dir"
    execute_cmd "unzip -q '$vsix_file' -d '$extract_dir'" "VSIX 압축 해제"
    
    log_success "VSIX 압축 해제됨: $extract_dir"
}

# --- VSIX 내용을 대상 디렉터리로 복사 ---
copy_vscode_extension() {
    local vsix_file="${1:-$VSIX_FILE}"
    local target_dir="${2:-$VSCODE_PLUGIN_TARGET_DIR}"
    
    if [[ -z "$vsix_file" ]]; then
        die "VSIX 파일이 지정되지 않았습니다."
    fi
    
    log_step "VSCode 확장 파일 복사 중..."
    
    # 대상 디렉터리 정리
    remove_dir "$target_dir"
    ensure_dir "$target_dir"
    
    # VSIX를 임시 디렉터리에 압축 해제
    local temp_extract_dir="$BUILD_TEMP_DIR/vsix_extract"
    extract_vsix "$vsix_file" "$temp_extract_dir"
    
    # 확장 파일 복사
    copy_files "$temp_extract_dir/extension" "$target_dir/" "VSCode 확장 파일"
    
    log_success "VSCode 확장 파일 복사 완료"
}

# --- 디버그 리소스 복사 (디버그 빌드용) ---
copy_debug_resources() {
    if [[ "$BUILD_MODE" != "$BUILD_MODE_DEBUG" ]]; then
        return 0
    fi

    log_step "디버그 리소스 복사 중..."

    local debug_res_dir="$PROJECT_ROOT/debug-resources"
    local vscode_plugin_debug_dir="${1:-$debug_res_dir/${VSCODE_PLUGIN_NAME}}"

    # 디버그 리소스 정리
    remove_dir "$debug_res_dir"
    ensure_dir "$vscode_plugin_debug_dir"

    cd "$PLUGIN_BUILD_DIR"

    # 다양한 디버그 리소스 복사
    copy_files "src/dist/i18n" "$vscode_plugin_debug_dir/dist/" "i18n 파일"
    copy_files "src/dist/extension.js" "$vscode_plugin_debug_dir/dist/" "extension.js"
    copy_files "src/dist/extension.js.map" "$vscode_plugin_debug_dir/dist/" "extension.js.map"

    # WASM 파일 복사
    find "$PLUGIN_BUILD_DIR/src/dist" -maxdepth 1 -name "*.wasm" -exec cp {} "$vscode_plugin_debug_dir/dist/" \;

    # assets 및 audio 복사
    copy_files "src/assets" "$vscode_plugin_debug_dir/" "assets"
    copy_files "src/webview-ui/audio" "$vscode_plugin_debug_dir/" "audio 파일"

    # webview 빌드 복사
    copy_files "src/webview-ui/build" "$vscode_plugin_debug_dir/webview-ui/" "webview 빌드"

    # 테마 파일 복사
    ensure_dir "$vscode_plugin_debug_dir/src/integrations/theme/default-themes"
    copy_files "src/integrations/theme/default-themes" "$vscode_plugin_debug_dir/src/integrations/theme/" "기본 테마"

    # IDEA 테마가 존재하면 복사
    local idea_themes_dir="$IDEA_BUILD_DIR/src/main/resources/themes"
    if [[ -d "$idea_themes_dir" ]]; then
        copy_files "$idea_themes_dir/*" "$vscode_plugin_debug_dir/src/integrations/theme/default-themes/" "IDEA 테마"
    fi

    # JSON 파일 복사 (특정 파일 제외)
    for json_file in "$PLUGIN_BUILD_DIR"/*.json; do
        local filename=$(basename "$json_file")
        if [[ "$filename" != "package-lock.json" && "$filename" != "tsconfig.json" ]]; then
            copy_files "$json_file" "$vscode_plugin_debug_dir/" "$filename"
        fi
    done

    # CommonJS 호환성을 위해 package.json에서 type 필드 제거
    local debug_package_json="$vscode_plugin_debug_dir/package.json"
    if [[ -f "$debug_package_json" ]]; then
        node -e "
            const fs = require('fs');
            const pkgPath = process.argv[1];
            const pkg = JSON.parse(fs.readFileSync(pkgPath, 'utf8'));
            delete pkg.type;
            fs.writeFileSync(pkgPath, JSON.stringify(pkg, null, 2));
            console.log('CommonJS 호환성을 위해 디버그 package.json에서 type 필드 제거');
        " "$debug_package_json"
    fi

    log_success "디버그 리소스 복사 완료"
}

# --- 기본 확장 빌드 ---
build_extension_host() {
    if [[ "$SKIP_BASE_BUILD" == "true" ]]; then
        log_info "Extension Host 빌드 건너뛰기"
        return 0
    fi
    
    log_step "Extension Host 빌드 중..."
    
    cd "$BASE_BUILD_DIR"
    
    # 이전 빌드 정리
    remove_dir "dist"
    
    # 모드에 따라 확장 빌드
    if [[ "$BUILD_MODE" == "$BUILD_MODE_DEBUG" ]]; then
        execute_cmd "npm run build" "Extension Host 빌드 (디버그)"
    else
        execute_cmd "npm run build:extension" "Extension Host 빌드 (릴리스)"
    fi
    
    # 프로덕션 의존성 목록 생성
    execute_cmd "npm ls --prod --depth=10 --parseable > '$IDEA_BUILD_DIR/prodDep.txt'" "프로덕션 의존성 목록"
    
    log_success "기본 확장 빌드 완료"
}

# --- 디버그용 기본 확장 복사 ---
copy_base_debug_resources() {
    if [[ "$BUILD_MODE" != "$BUILD_MODE_DEBUG" ]]; then
        return 0
    fi
    
    log_step "기본 디버그 리소스 복사 중..."
    
    local debug_res_dir="$PROJECT_ROOT/debug-resources"
    local runtime_dir="$debug_res_dir/runtime"
    local node_modules_dir="$debug_res_dir/node_modules"
    
    ensure_dir "$runtime_dir"
    ensure_dir "$node_modules_dir"
    
    # node_modules 복사
    copy_files "$BASE_BUILD_DIR/node_modules/*" "$node_modules_dir/" "기본 node_modules"
    
    # package.json 및 dist 복사
    copy_files "$BASE_BUILD_DIR/package.json" "$runtime_dir/" "기본 package.json"
    copy_files "$BASE_BUILD_DIR/dist/*" "$runtime_dir/" "기본 dist 파일"
    
    log_success "기본 디버그 리소스 복사 완료"
}

# --- IDEA 플러그인 빌드 ---
build_idea_plugin() {
    if [[ "$SKIP_IDEA_BUILD" == "true" ]]; then
        log_info "IDEA 플러그인 빌드 건너뛰기"
        return 0
    fi
    
    log_step "Building IDEA plugin..."
    
    cd "$IDEA_BUILD_DIR"
    
    # Gradle 빌드 파일 확인
    if [[ ! -f "build.gradle" && ! -f "build.gradle.kts" ]]; then
        die "IDEA 디렉터리에서 Gradle 빌드 파일을 찾을 수 없습니다."
    fi
    
    # gradlew가 있으면 사용하고, 없으면 시스템 gradle 사용
    local gradle_cmd="gradle"
    if [[ -f "./gradlew" ]]; then
        gradle_cmd="./gradlew"
        chmod +x "./gradlew"
    fi
    
    # BUILD_MODE에 따라 debugMode 설정
    local debug_mode="none"
    if [[ "$BUILD_MODE" == "$BUILD_MODE_RELEASE" ]]; then
        debug_mode="release"
        log_info "릴리스 모드로 IDEA 플러그인 빌드 중 (debugMode=release)"
    elif [[ "$BUILD_MODE" == "$BUILD_MODE_DEBUG" ]]; then
        debug_mode="idea"
        log_info "디버그 모드로 IDEA 플러그인 빌드 중 (debugMode=idea)"
    fi
    
    # debugMode 속성과 함께 플러그인 빌드
    execute_cmd "$gradle_cmd -PdebugMode=$debug_mode buildPlugin --info" "IDEA 플러그인 빌드"
    
    # 생성된 플러그인 찾기
    local plugin_file
    plugin_file=$(find "$IDEA_BUILD_DIR/build/distributions" \( -name "*.zip" -o -name "*.jar" \) -type f | sort -r | head -n 1)
    
    if [[ -n "$plugin_file" ]]; then
        log_success "IDEA 플러그인 빌드됨: $plugin_file"
        export IDEA_PLUGIN_FILE="$plugin_file"
    else
        log_warn "build/distributions에서 IDEA 플러그인 파일을 찾을 수 없습니다."
    fi
}

# --- 빌드 결과물 정리 ---
clean_build() {
    log_step "빌드 결과물 정리 중..."
    
    # VSCode 빌드 정리
    if [[ -d "$PLUGIN_BUILD_DIR" ]]; then
        cd "$PLUGIN_BUILD_DIR"
        [[ -d "bin" ]] && remove_dir "bin"
        [[ -d "src/dist" ]] && remove_dir "src/dist"
        [[ -d "node_modules" ]] && remove_dir "node_modules"
    fi
    
    # 기본 빌드 정리
    if [[ -d "$BASE_BUILD_DIR" ]]; then
        cd "$BASE_BUILD_DIR"
        [[ -d "dist" ]] && remove_dir "dist"
        [[ -d "node_modules" ]] && remove_dir "node_modules"
    fi
    
    # IDEA 빌드 정리
    if [[ -d "$IDEA_BUILD_DIR" ]]; then
        cd "$IDEA_BUILD_DIR"
        [[ -d "build" ]] && remove_dir "build"
        [[ -d "$VSCODE_PLUGIN_TARGET_DIR" ]] && remove_dir "$VSCODE_PLUGIN_TARGET_DIR"
    fi
    
    # 디버그 리소스 정리
    [[ -d "$PROJECT_ROOT/debug-resources" ]] && remove_dir "$PROJECT_ROOT/debug-resources"
    
    # 임시 디렉터리 정리
    find /tmp -name "${TEMP_PREFIX}*" -type d -exec rm -rf {} + 2>/dev/null || true
    
    log_success "빌드 결과물 정리 완료"
}

# --- 빌드 환경 정리 ---
cleanup_build() {
    if [[ -n "${BUILD_TEMP_DIR:-}" && -d "${BUILD_TEMP_DIR:-}" ]]; then
        remove_dir "$BUILD_TEMP_DIR"
    fi
}

# --- 정리 트랩 설정 ---
trap cleanup_build EXIT
