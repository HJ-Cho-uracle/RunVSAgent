#!/bin/bash

# RunVSAgent 프로젝트 빌드 스크립트
# 이 스크립트는 VSCode 확장 및 IDEA 플러그인을 빌드합니다.

# 오류 발생 시 즉시 종료, 정의되지 않은 변수 사용 시 오류, 파이프라인 오류 시 오류
set -euo pipefail

# 공통 유틸리티 스크립트 로드
source "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/lib/common.sh"
source "$SCRIPT_DIR/lib/build.sh"

# --- 스크립트 설정 ---
readonly SCRIPT_NAME="build.sh"
readonly SCRIPT_VERSION="1.0.0"

# --- 빌드 대상 정의 ---
readonly TARGET_ALL="all"       # 모든 컴포넌트 빌드
readonly TARGET_VSCODE="vscode" # VSCode 확장만 빌드
readonly TARGET_BASE="base"     # 기본 확장 런타임만 빌드
readonly TARGET_IDEA="idea"     # IDEA 플러그인만 빌드

# --- 빌드 구성 변수 ---
BUILD_TARGET="$TARGET_ALL"      # 기본 빌드 대상은 'all'
CLEAN_BEFORE_BUILD=false        # 빌드 전에 정리할지 여부
SKIP_TESTS=false                # 테스트 건너뛸지 여부
OUTPUT_DIR=""                   # 빌드 결과물 출력 디렉터리
# VSIX_FILE, SKIP_VSCODE_BUILD, SKIP_BASE_BUILD, SKIP_IDEA_BUILD는 lib/build.sh에서 정의됨

# --- 스크립트 도움말 표시 ---
show_help() {
    cat << EOF
$SCRIPT_NAME - RunVSAgent 프로젝트 컴포넌트 빌드

사용법:
    $SCRIPT_NAME [옵션] [대상]

설명:
    이 스크립트는 RunVSAgent 프로젝트의 다음 컴포넌트를 빌드합니다:
    - VSCode 확장 (서브모듈에서)
    - 기본 확장 런타임
    - IDEA 플러그인

대상:
    all         모든 컴포넌트 빌드 (기본값)
    vscode      VSCode 확장만 빌드
    base        기본 확장만 빌드
    idea        IDEA 플러그인만 빌드

옵션:
    -m, --mode MODE       빌드 모드: release (기본값) 또는 debug
    -c, --clean           빌드 전에 정리
    -o, --output DIR      빌드 결과물 출력 디렉터리
    -t, --skip-tests      테스트 실행 건너뛰기
    --vsix FILE           기존 VSIX 파일 사용 (VSCode 빌드 건너뛰기)
    --skip-vscode         VSCode 확장 빌드 건너뛰기
    --skip-base           기본 확장 빌드 건너뛰기
    --skip-idea           IDEA 플러그인 빌드 건너뛰기
    -v, --verbose         자세한 출력 활성화
    -n, --dry-run         실행하지 않고 수행될 작업 표시
    -h, --help            이 도움말 메시지 표시

빌드 모드:
    release     최적화된 프로덕션 빌드 (기본값)
    debug       디버그 심볼 및 리소스가 포함된 개발 빌드

예시:
    $SCRIPT_NAME                           # 모든 컴포넌트 빌드
    $SCRIPT_NAME --mode debug              # 디버그 빌드
    $SCRIPT_NAME --clean vscode            # VSCode만 정리 후 빌드
    $SCRIPT_NAME --vsix path/to/file.vsix  # 기존 VSIX 사용
    $SCRIPT_NAME --output ./dist           # 사용자 정의 출력 디렉터리

환경 변수:
    BUILD_MODE          빌드 모드 재정의 (release/debug)
    VSIX_FILE           기존 VSIX 파일 경로
    SKIP_VSCODE_BUILD   'true'로 설정하면 VSCode 빌드 건너뛰기
    SKIP_BASE_BUILD     'true'로 설정하면 기본 빌드 건너뛰기
    SKIP_IDEA_BUILD     'true'로 설정하면 IDEA 빌드 건너뛰기

종료 코드:
    0    성공
    1    일반 오류
    2    빌드 실패
    3    잘못된 인자
    4    누락된 의존성

EOF
}

# --- 명령줄 인자 파싱 ---
parse_build_args() {
    while [[ $# -gt 0 ]]; do
        case $1 in
            -m|--mode)
                if [[ -z "${2:-}" ]]; then
                    log_error "빌드 모드에 값이 필요합니다."
                    exit 3
                fi
                BUILD_MODE="$2"
                shift 2
                ;;
            -c|--clean)
                CLEAN_BEFORE_BUILD=true
                shift
                ;;
            -o|--output)
                if [[ -z "${2:-}" ]]; then
                    log_error "출력 디렉터리에 값이 필요합니다."
                    exit 3
                fi
                OUTPUT_DIR="$2"
                shift 2
                ;;
            -t|--skip-tests)
                SKIP_TESTS=true
                shift
                ;;
            --vsix)
                if [[ -z "${2:-}" ]]; then
                    log_error "VSIX 파일 경로에 값이 필요합니다."
                    exit 3
                fi
                VSIX_FILE="$2"
                SKIP_VSCODE_BUILD=true # VSIX 파일을 사용하면 VSCode 빌드를 건너뜁니다.
                shift 2
                ;;
            --skip-vscode)
                SKIP_VSCODE_BUILD=true
                shift
                ;;
            --skip-base)
                SKIP_BASE_BUILD=true
                shift
                ;;
            --skip-idea)
                SKIP_IDEA_BUILD=true
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
                BUILD_TARGET="$1"
                shift
                ;;
        esac
    done
    
    # 빌드 모드 유효성 검사
    if [[ "$BUILD_MODE" != "$BUILD_MODE_RELEASE" && "$BUILD_MODE" != "$BUILD_MODE_DEBUG" ]]; then
        log_error "유효하지 않은 빌드 모드: $BUILD_MODE"
        log_info "유효한 모드: $BUILD_MODE_RELEASE, $BUILD_MODE_DEBUG"
        exit 3
    fi
    
    # 빌드 대상 유효성 검사
    case "$BUILD_TARGET" in
        "$TARGET_ALL"|"$TARGET_VSCODE"|"$TARGET_BASE"|"$TARGET_IDEA")
            ;;
        *)
            log_error "유효하지 않은 빌드 대상: $BUILD_TARGET"
            log_info "유효한 대상: $TARGET_ALL, $TARGET_VSCODE, $TARGET_BASE, $TARGET_IDEA"
            exit 3
            ;;
    esac
    
    # 대상에 따라 건너뛰기 플래그 설정
    case "$BUILD_TARGET" in
        "$TARGET_VSCODE")
            SKIP_BASE_BUILD=true
            SKIP_IDEA_BUILD=true
            ;;
        "$TARGET_BASE")
            SKIP_VSCODE_BUILD=true
            SKIP_IDEA_BUILD=true
            ;;
        "$TARGET_IDEA")
            SKIP_VSCODE_BUILD=true
            SKIP_BASE_BUILD=true
            ;;
    esac
    
    # 환경 변수로 재정의
    [[ "${SKIP_VSCODE_BUILD:-false}" == "true" ]] && SKIP_VSCODE_BUILD=true
    [[ "${SKIP_BASE_BUILD:-false}" == "true" ]] && SKIP_BASE_BUILD=true
    [[ "${SKIP_IDEA_BUILD:-false}" == "true" ]] && SKIP_IDEA_BUILD=true
    [[ -n "${VSIX_FILE:-}" ]] && VSIX_FILE="$VSIX_FILE"
    
    # 함수가 성공 시 0을 반환하도록 하여 `set -e`가 스크립트를 종료하지 않도록 합니다.
    true
}

# --- JDK 버전 확인 ---
check_jdk_version() {
    log_step "JDK 버전 확인 중..."
    
    # java 명령어가 존재하는지 확인
    if ! command_exists "java"; then
        log_error "Java를 찾을 수 없습니다. JDK 17 이상을 설치해주세요."
        exit 4
    fi
    
    # Java 버전 가져오기
    local java_version_output
    java_version_output=$(java -version 2>&1 | head -n 1)
    
    # 출력에서 버전 번호 추출
    local java_version
    if [[ "$java_version_output" =~ \"([0-9]+)\.([0-9]+)\.([0-9]+) ]]; then
        # Java 8 이하 형식: "1.8.0_xxx"
        if [[ "${BASH_REMATCH[1]}" == "1" ]]; then
            java_version="${BASH_REMATCH[2]}"
        else
            java_version="${BASH_REMATCH[1]}"
        fi
    elif [[ "$java_version_output" =~ \"([0-9]+) ]]; then
        # Java 9+ 형식: "17.0.1" 또는 "17"
        java_version="${BASH_REMATCH[1]}"
    else
        log_error "Java 버전 파싱 실패: $java_version_output"
        exit 4
    fi
    
    log_debug "감지된 Java 버전: $java_version"
    
    # 버전이 17 이상인지 확인
    if [[ "$java_version" -lt 17 ]]; then
        log_error "JDK 버전 $java_version이 너무 오래되었습니다. 요구 사항: JDK 17 이상."
        log_info "현재 Java 버전 출력: $java_version_output"
        exit 4
    fi
    
    log_success "JDK 버전 확인 통과 (버전: $java_version)"
}

# --- 빌드 환경 유효성 검사 ---
validate_build_environment() {
    log_step "빌드 환경 유효성 검사 중..."
    
    # JDK 버전 확인
    check_jdk_version
    
    # setup 스크립트가 실행되었는지 확인 (VSCode 서브모듈 초기화 여부)
    local vscode_dir="$PROJECT_ROOT/$VSCODE_SUBMODULE_PATH"
    if [[ ! -d "$vscode_dir" ]] || [[ ! "$(ls -A "$vscode_dir" 2>/dev/null)" ]]; then
        log_error "VSCode 서브모듈이 초기화되지 않았습니다. 먼저 './scripts/setup.sh'를 실행하세요."
        exit 4
    fi
    
    # 필요한 빌드 파일 확인
    if [[ "$SKIP_BASE_BUILD" != "true" ]]; then
        if [[ ! -f "$PROJECT_ROOT/$EXTENSION_HOST_DIR/package.json" ]]; then
            log_error "기본 package.json을 찾을 수 없습니다. 먼저 './scripts/setup.sh'를 실행하세요."
            exit 4
        fi
    fi
    
    if [[ "$SKIP_IDEA_BUILD" != "true" ]]; then
        if [[ ! -f "$PROJECT_ROOT/$IDEA_DIR/build.gradle" && ! -f "$PROJECT_ROOT/$IDEA_DIR/build.gradle.kts" ]]; then
            log_error "IDEA Gradle 빌드 파일을 찾을 수 없습니다."
            exit 4
        fi
    fi
    
    # VSIX 파일이 제공된 경우 유효성 검사
    if [[ -n "$VSIX_FILE" ]]; then
        if [[ ! -f "$VSIX_FILE" ]]; then
            log_error "VSIX 파일을 찾을 수 없습니다: $VSIX_FILE"
            exit 4
        fi
        log_info "기존 VSIX 파일 사용 중: $VSIX_FILE"
    fi
    
    log_success "빌드 환경 유효성 검사 완료"
}

# --- 빌드 출력 디렉터리 설정 ---
setup_output_directory() {
    if [[ -n "$OUTPUT_DIR" ]]; then
        log_step "출력 디렉터리 설정 중..."
        
        ensure_dir "$OUTPUT_DIR" # 디렉터리가 없으면 생성
        
        # 출력 디렉터리 경로를 절대 경로로 만듭니다.
        OUTPUT_DIR="$(cd "$OUTPUT_DIR" && pwd)"
        
        log_info "빌드 결과물이 다음으로 복사됩니다: $OUTPUT_DIR"
    fi
}

# --- 빌드 결과물 정리 ---
clean_build_artifacts() {
    if [[ "$CLEAN_BEFORE_BUILD" != "true" ]]; then
        return 0
    fi
    
    log_step "빌드 결과물 정리 중..."
    clean_build # lib/build.sh에 정의된 clean_build 함수 호출
    log_success "빌드 결과물 정리 완료"
}

# --- VSCode 확장 컴포넌트 빌드 ---
build_vscode_plugin_component() {
    if [[ "$SKIP_VSCODE_BUILD" == "true" ]]; then
        log_info "VSCode 확장 빌드 건너뛰기"
        return 0
    fi
    
    log_step "VSCode 확장 빌드 중..."
    
    build_vscode_extension # lib/build.sh에 정의된 함수 호출
    copy_vscode_extension # lib/build.sh에 정의된 함수 호출
    
    # 디버그 모드인 경우 디버그 리소스 복사
    local debug_res_dir="$PROJECT_ROOT/debug-resources"
    copy_debug_resources $debug_res_dir/${VSCODE_PLUGIN_NAME} # lib/build.sh에 정의된 함수 호출
    
    log_success "VSCode 확장 빌드 완료"
}

# --- 기본 확장 런타임 컴포넌트 빌드 ---
build_vscode_extension_host_component() {
    if [[ "$SKIP_BASE_BUILD" == "true" ]]; then
        log_info "Extension Host 빌드 건너뛰기"
        return 0
    fi
    
    log_step "Extension Host 빌드 중..."
    
    build_extension_host # lib/build.sh에 정의된 함수 호출
    copy_base_debug_resources # lib/build.sh에 정의된 함수 호출
    
    log_success "Extension Host 빌드 완료"
}

# --- IDEA 플러그인 컴포넌트 빌드 ---
build_idea_component() {
    if [[ "$SKIP_IDEA_BUILD" == "true" ]]; then
        log_info "IDEA 플러그인 빌드 건너뛰기"
        return 0
    fi
    
    log_step "IDEA 플러그인 빌드 중..."
    
    build_idea_plugin # lib/build.sh에 정의된 함수 호출
    
    log_success "IDEA 플러그인 빌드 완료"
}

# --- 테스트 실행 ---
run_tests() {
    if [[ "$SKIP_TESTS" == "true" ]]; then
        log_info "테스트 건너뛰기"
        return 0
    fi
    
    log_step "테스트 실행 중..."
    
    # 기본 확장 테스트 실행 (사용 가능한 경우)
    if [[ "$SKIP_BASE_BUILD" != "true" && -f "$PROJECT_ROOT/$EXTENSION_HOST_DIR/package.json" ]]; then
        cd "$PROJECT_ROOT/$EXTENSION_HOST_DIR"
        if npm run test --if-present >/dev/null 2>&1; then
            execute_cmd "npm test" "기본 확장 테스트"
        else
            log_debug "기본 확장에 대한 테스트를 찾을 수 없습니다."
        fi
    fi
    
    # VSCode 확장 테스트 실행 (사용 가능한 경우)
    if [[ "$SKIP_VSCODE_BUILD" != "true" && -d "$PROJECT_ROOT/$VSCODE_SUBMODULE_PATH" ]]; then
        cd "$PROJECT_ROOT/$VSCODE_SUBMODULE_PATH"
        local pkg_manager="npm"
        if command_exists "pnpm" && [[ -f "pnpm-lock.yaml" ]]; then
            pkg_manager="pnpm"
        fi
        
        if $pkg_manager run test --if-present >/dev/null 2>&1; then
            execute_cmd "$pkg_manager test" "VSCode 확장 테스트"
        else
            log_debug "VSCode 확장에 대한 테스트를 찾을 수 없습니다."
        fi
    fi
    
    log_success "테스트 완료"
}

# --- 빌드 결과물 출력 디렉터리로 복사 ---
copy_build_artifacts() {
    if [[ -z "$OUTPUT_DIR" ]]; then
        return 0
    fi
    
    log_step "빌드 결과물을 출력 디렉터리로 복사 중..."
    
    # VSIX 파일 복사
    if [[ -n "$VSIX_FILE" && -f "$VSIX_FILE" ]]; then
        copy_files "$VSIX_FILE" "$OUTPUT_DIR/" "VSIX 파일"
    fi
    
    # IDEA 플러그인 복사
    if [[ -n "${IDEA_PLUGIN_FILE:-}" && -f "$IDEA_PLUGIN_FILE" ]]; then
        copy_files "$IDEA_PLUGIN_FILE" "$OUTPUT_DIR/" "IDEA 플러그인"
    fi
    
    # 디버그 모드인 경우 디버그 리소스 복사
    if [[ "$BUILD_MODE" == "$BUILD_MODE_DEBUG" && -d "$PROJECT_ROOT/debug-resources" ]]; then
        copy_files "$PROJECT_ROOT/debug-resources" "$OUTPUT_DIR/" "디버그 리소스"
    fi
    
    log_success "빌드 결과물 출력 디렉터리로 복사 완료"
}

# --- 빌드 요약 표시 ---
show_build_summary() {
    log_step "빌드 요약"
    
    echo ""
    log_info "빌드 성공적으로 완료되었습니다!"
    log_info "빌드 모드: $BUILD_MODE"
    log_info "빌드 대상: $BUILD_TARGET"
    log_info "플랫폼: $(get_platform)"
    
    echo ""
    log_info "생성된 결과물:"
    
    # VSIX 파일 표시
    if [[ -n "$VSIX_FILE" && -f "$VSIX_FILE" ]]; then
        log_info "  VSCode 확장: $VSIX_FILE"
    fi
    
    # IDEA 플러그인 표시
    if [[ -n "${IDEA_PLUGIN_FILE:-}" && -f "$IDEA_PLUGIN_FILE" ]]; then
        log_info "  IDEA 플러그인: $IDEA_PLUGIN_FILE"
    fi
    
    # 디버그 리소스 표시
    if [[ "$BUILD_MODE" == "$BUILD_MODE_DEBUG" && -d "$PROJECT_ROOT/debug-resources" ]]; then
        log_info "  디버그 리소스: $PROJECT_ROOT/debug-resources"
    fi
    
    # 출력 디렉터리 표시
    if [[ -n "$OUTPUT_DIR" ]]; then
        log_info "  출력 디렉터리: $OUTPUT_DIR"
    fi
    
    echo ""
    log_info "다음 단계:"
    if [[ "$BUILD_TARGET" == "$TARGET_ALL" || "$BUILD_TARGET" == "$TARGET_IDEA" ]]; then
        log_info "  1. IDEA 플러그인 설치: ${IDEA_PLUGIN_FILE:-$IDEA_BUILD_DIR/build/distributions/}"
        log_info "  2. IDEA에서 플러그인 설정"
    fi
    
    echo ""
}

# --- 메인 빌드 함수 ---
main() {
    log_info "RunVSAgent 빌드 프로세스 시작..."
    log_info "스크립트: $SCRIPT_NAME v$SCRIPT_VERSION"
    log_info "플랫폼: $(get_platform)"
    log_info "프로젝트 루트: $PROJECT_ROOT"
    
    if [[ "$DRY_RUN" == "true" ]]; then
        log_warn "DRY RUN 모드 - 변경 사항이 적용되지 않습니다."
    fi
    
    # 인자 파싱
    parse_build_args "$@"
    
    log_info "빌드 구성:"
    log_info "  모드: $BUILD_MODE"
    log_info "  대상: $BUILD_TARGET"
    log_info "  정리: $CLEAN_BEFORE_BUILD"
    log_info "  테스트 건너뛰기: $SKIP_TESTS"
    [[ -n "$OUTPUT_DIR" ]] && log_info "  출력: $OUTPUT_DIR"
    
    # 빌드 환경 초기화
    init_build_env
    
    # 빌드 단계 실행
    validate_build_environment
    setup_output_directory
    clean_build_artifacts
    
    # 컴포넌트 빌드
    build_vscode_plugin_component
    build_vscode_extension_host_component
    build_idea_component
    
    # 테스트 실행 및 마무리
    #run_tests
    copy_build_artifacts
    show_build_summary
    
    log_success "빌드 프로세스 성공적으로 완료되었습니다!"
}

# 모든 인자와 함께 메인 함수 실행
main "$@"
