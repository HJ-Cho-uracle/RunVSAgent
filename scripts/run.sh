#!/bin/bash

# RunVSAgent 프로젝트의 메인 진입점 스크립트
# 이 스크립트는 모든 프로젝트 작업에 대한 통합 인터페이스를 제공합니다.

# 오류 발생 시 즉시 종료, 정의되지 않은 변수 사용 시 오류, 파이프라인 오류 시 오류
set -euo pipefail

# 공통 유틸리티 스크립트 로드
source "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/lib/common.sh"

# --- 스크립트 설정 ---
readonly SCRIPT_NAME="run.sh"
readonly SCRIPT_VERSION="1.0.0"

# --- 사용 가능한 명령어 ---
readonly CMD_SETUP="setup"   # 개발 환경 초기화
readonly CMD_BUILD="build"   # 프로젝트 컴포넌트 빌드
readonly CMD_CLEAN="clean"   # 빌드 결과물 및 임시 파일 정리
readonly CMD_TEST="test"     # 테스트 및 유효성 검사 실행
readonly CMD_HELP="help"     # 명령어 도움말 표시
readonly CMD_VERSION="version" # 버전 정보 표시

# --- 메인 도움말 표시 ---
show_main_help() {
    cat << EOF
$SCRIPT_NAME - RunVSAgent 프로젝트 관리 도구

사용법:
    $SCRIPT_NAME <명령어> [옵션]

설명:
    이 스크립트는 RunVSAgent 프로젝트 작업을 위한 메인 진입점입니다.
    프로젝트 컴포넌트 설정, 빌드, 테스트 및 유지 관리를 위한 통합 인터페이스를 제공합니다.

명령어:
    setup       개발 환경 초기화
    build       프로젝트 컴포넌트 빌드
    clean       빌드 결과물 및 임시 파일 정리
    test        테스트 및 유효성 검사 실행
    help        명령어 도움말 표시
    version     버전 정보 표시

전역 옵션:
    -v, --verbose    자세한 출력 활성화
    -h, --help       이 도움말 메시지 표시

예시:
    $SCRIPT_NAME setup                    # 개발 환경 초기화
    $SCRIPT_NAME build                    # 모든 컴포넌트 빌드
    $SCRIPT_NAME build --mode debug       # 디버그 빌드
    $SCRIPT_NAME clean all                # 모든 것 정리
    $SCRIPT_NAME test                     # 모든 테스트 실행
    $SCRIPT_NAME help build               # build 명령어 도움말 표시

시작하기:
    1. $SCRIPT_NAME setup                 # 첫 번째 설정
    2. $SCRIPT_NAME build                 # 프로젝트 빌드
    3. $SCRIPT_NAME test                  # 빌드 유효성 검사

각 명령어에 대한 자세한 도움말을 보려면 다음을 사용하세요:
    $SCRIPT_NAME help <명령어>

프로젝트 구조:
    base/           기본 확장 런타임
    idea/           IDEA 플러그인 소스
    deps/           의존성 및 서브모듈
    scripts/        빌드 및 유지 관리 스크립트

환경 변수:
    동작을 사용자 정의하려면 다음 환경 변수를 설정하세요:
    - VERBOSE=true          자세한 출력 활성화
    - DRY_RUN=true         실행하지 않고 수행될 작업 표시
    - BUILD_MODE=debug     빌드 모드 설정
    - SKIP_VALIDATION=true 환경 유효성 검사 건너뛰기

EOF
}

# --- 명령어별 도움말 표시 ---
show_command_help() {
    local command="$1"
    
    case "$command" in
        "$CMD_SETUP")
            "$SCRIPT_DIR/setup.sh" --help # setup.sh 스크립트의 도움말 표시
            ;;
        "$CMD_BUILD")
            "$SCRIPT_DIR/build.sh" --help # build.sh 스크립트의 도움말 표시
            ;;
        "$CMD_CLEAN")
            "$SCRIPT_DIR/clean.sh" --help # clean.sh 스크립트의 도움말 표시
            ;;
        "$CMD_TEST")
            "$SCRIPT_DIR/test.sh" --help # test.sh 스크립트의 도움말 표시
            ;;
        *)
            log_error "알 수 없는 명령어: $command"
            log_info "사용 가능한 명령어: $CMD_SETUP, $CMD_BUILD, $CMD_CLEAN, $CMD_TEST"
            exit 1
            ;;
    esac
}

# --- 버전 정보 표시 ---
show_version() {
    cat << EOF
$SCRIPT_NAME 버전 $SCRIPT_VERSION

RunVSAgent 프로젝트 관리 도구
플랫폼: $(get_platform)
셸: $SHELL
프로젝트 루트: $PROJECT_ROOT

컴포넌트 버전:
- Node.js: $(node --version 2>/dev/null || echo "찾을 수 없음")
- NPM: $(npm --version 2>/dev/null || echo "찾을 수 없음")
- Git: $(git --version 2>/dev/null || echo "찾을 수 없음")
- Gradle: $(gradle --version 2>/dev/null | head -n 1 || echo "찾을 수 없음")

빌드 도구:
- pnpm: $(command_exists "pnpm" && echo "사용 가능" || echo "찾을 수 없음")
- shellcheck: $(command_exists "shellcheck" && echo "사용 가능" || echo "찾을 수 없음")

EOF
}

# --- 명령어 유효성 검사 ---
validate_command() {
    local command="$1"
    
    case "$command" in
        "$CMD_SETUP"|"$CMD_BUILD"|"$CMD_CLEAN"|"$CMD_TEST"|"$CMD_HELP"|"$CMD_VERSION")
            return 0 # 유효한 명령어
            ;;
        *)
            log_error "알 수 없는 명령어: $command"
            log_info "사용 가능한 명령어: $CMD_SETUP, $CMD_BUILD, $CMD_CLEAN, $CMD_TEST, $CMD_HELP, $CMD_VERSION"
            log_info "자세한 정보는 '$SCRIPT_NAME help'를 사용하세요."
            return 1 # 유효하지 않은 명령어
            ;;
    esac
}

# --- 명령어 실행 ---
execute_command() {
    local command="$1"
    shift # 첫 번째 인자(명령어)를 제거하고 나머지 인자만 남깁니다.
    
    case "$command" in
        "$CMD_SETUP")
            exec "$SCRIPT_DIR/setup.sh" "$@" # setup.sh 실행
            ;;
        "$CMD_BUILD")
            exec "$SCRIPT_DIR/build.sh" "$@" # build.sh 실행
            ;;
        "$CMD_CLEAN")
            exec "$SCRIPT_DIR/clean.sh" "$@" # clean.sh 실행
            ;;
        "$CMD_TEST")
            exec "$SCRIPT_DIR/test.sh" "$@" # test.sh 실행
            ;;
        "$CMD_HELP")
            if [[ $# -gt 0 ]]; then
                show_command_help "$1" # 특정 명령어에 대한 도움말
            else
                show_main_help # 메인 도움말
            fi
            ;;
        "$CMD_VERSION")
            show_version # 버전 정보 표시
            ;;
        *)
            log_error "명령어 실행 실패: $command"
            exit 1
            ;;
    esac
}

# --- 전역 옵션 파싱 ---
parse_global_options() {
    while [[ $# -gt 0 ]]; do
        case $1 in
            -v|--verbose)
                VERBOSE=true
                export VERBOSE # VERBOSE 변수를 하위 스크립트에서도 사용할 수 있도록 export
                shift
                ;;
            -h|--help)
                show_main_help
                exit 0
                ;;
            -*)
                # 알 수 없는 전역 옵션은 명령어로 전달
                break
                ;;
            *)
                # 옵션이 아니면 명령어이므로 루프 종료
                break
                ;;
        esac
    done
    
    # 남은 인자 반환
    echo "$@"
}

# --- 첫 실행 설정 확인 ---
check_first_time_setup() {
    local vscode_dir="$PROJECT_ROOT/$VSCODE_SUBMODULE_PATH"
    local base_node_modules="$PROJECT_ROOT/$EXTENSION_HOST_DIR/node_modules"
    
    # 첫 실행으로 보이는 경우 (VSCode 서브모듈 또는 기본 node_modules가 없는 경우)
    if [[ ! -d "$vscode_dir" ]] || [[ ! "$(ls -A "$vscode_dir" 2>/dev/null)" ]] || [[ ! -d "$base_node_modules" ]]; then
        log_warn "프로젝트를 처음 실행하는 것 같습니다."
        log_info "먼저 setup을 실행해야 할 수 있습니다:"
        log_info "  $SCRIPT_NAME setup"
        echo ""
    fi
}

# --- 메인 함수 ---
main() {
    # 전역 옵션 먼저 파싱
    local remaining_args
    remaining_args=$(parse_global_options "$@")
    eval set -- "$remaining_args" # 파싱된 인자로 $@ 재설정
    
    # 명령어가 지정되었는지 확인
    if [[ $# -eq 0 ]]; then
        log_info "RunVSAgent 프로젝트 관리 도구 v$SCRIPT_VERSION"
        log_info "플랫폼: $(get_platform)"
        log_info "프로젝트 루트: $PROJECT_ROOT"
        echo ""
        
        check_first_time_setup # 첫 실행 설정 확인
        
        log_error "명령어가 지정되지 않았습니다."
        log_info "사용법 정보는 '$SCRIPT_NAME help'를 사용하세요."
        exit 1
    fi
    
    local command="$1"
    shift # 명령어 인자를 제거
    
    # 명령어 유효성 검사 및 실행
    if validate_command "$command"; then
        if [[ "$VERBOSE" == "true" ]]; then
            log_debug "명령어 실행 중: $command (인자: $*)"
        fi
        execute_command "$command" "$@"
    else
        exit 1
    fi
}

# 모든 인자와 함께 메인 함수 실행
main "$@"
