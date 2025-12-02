#!/bin/bash

# 빌드 스크립트를 위한 공통 유틸리티 함수
# 이 파일은 크로스 플랫폼 호환 유틸리티 함수를 제공합니다.

# --- 다중 소싱 방지 ---
# 스크립트가 여러 번 소싱되는 것을 방지합니다.
if [[ -n "${_COMMON_SH_LOADED:-}" ]]; then
    return 0
fi
_COMMON_SH_LOADED=1

# --- 출력 색상 코드 ---
readonly RED='\033[0;31m'
readonly GREEN='\033[0;32m'
readonly YELLOW='\033[0;33m'
readonly BLUE='\033[0;34m'
readonly PURPLE='\033[0;35m'
readonly CYAN='\033[0;36m'
readonly NC='\033[0m' # 색상 없음 (리셋)

# --- 전역 변수 ---
SCRIPT_DIR=""   # 현재 스크립트의 디렉터리
PROJECT_ROOT="" # 프로젝트의 루트 디렉터리
VERBOSE=false   # 자세한 출력 활성화 여부
DRY_RUN=false   # 드라이 런 모드 활성화 여부

# --- 프로젝트 구조 상수 ---
export VSCODE_SUBMODULE_PATH="deps/vscode" # VSCode 서브모듈 경로
export PLUGIN_SUBMODULE_PATH="deps/roo-code" # 플러그인 서브모듈 경로 (이전 roo-code)
export ATHENA_SUBMODULE_PATH="deps/athena" # Athena 서브모듈 경로 (현재 사용)
export EXTENSION_HOST_DIR="extension_host" # Extension Host 디렉터리

# --- 로깅 함수 ---
log_info() {
    echo -e "${GREEN}[INFO]${NC} $1" >&2 # 표준 에러로 출력
}

log_warn() {
    echo -e "${YELLOW}[WARN]${NC} $1" >&2
}

log_error() {
    echo -e "${RED}[ERROR]${NC} $1" >&2
}

log_debug() {
    if [[ "$VERBOSE" == "true" ]]; then
        echo -e "${BLUE}[DEBUG]${NC} $1" >&2
    fi
}

log_success() {
    echo -e "${GREEN}[SUCCESS]${NC} $1" >&2
}

log_step() {
    echo -e "${CYAN}[STEP]${NC} $1" >&2
}

# --- 오류 처리 ---
die() {
    log_error "$1"
    exit "${2:-1}" # 기본 종료 코드는 1
}

# --- 명령어 존재 여부 확인 ---
command_exists() {
    command -v "$1" >/dev/null 2>&1
}

# --- 명령어 실행 결과 확인 ---
check_result() {
    local exit_code=$?
    local error_msg="$1"
    local success_msg="$2"
    
    if [[ $exit_code -ne 0 ]]; then
        die "$error_msg" $exit_code
    elif [[ -n "$success_msg" ]]; then
        log_success "$success_msg"
    fi
}

# --- 디렉터리 존재 확인 및 생성 ---
ensure_dir() {
    local dir="$1"
    if [[ ! -d "$dir" ]]; then
        if [[ "$DRY_RUN" == "true" ]]; then
            log_debug "디렉터리 생성 예정: $dir"
        else
            mkdir -p "$dir"
            check_result "디렉터리 생성 실패: $dir" "디렉터리 생성됨: $dir"
        fi
    fi
}

# --- 디렉터리 안전하게 제거 ---
remove_dir() {
    local dir="$1"
    if [[ -d "$dir" ]]; then
        if [[ "$DRY_RUN" == "true" ]]; then
            log_debug "디렉터리 제거 예정: $dir"
        else
            rm -rf "$dir"
            check_result "디렉터리 제거 실패: $dir" "디렉터리 제거됨: $dir"
        fi
    fi
}

# --- 파일/디렉터리 복사 ---
copy_files() {
    local src="$1"
    local dest="$2"
    local description="${3:-파일}" # 기본 설명은 "파일"
    
    if [[ "$DRY_RUN" == "true" ]]; then
        log_debug "$description 을 $src 에서 $dest 로 복사 예정"
    else
        cp -r "$src" "$dest"
        check_result "$description 을 $src 에서 $dest 로 복사 실패" "$description 복사 성공"
    fi
}

# --- 로깅과 함께 명령어 실행 ---
execute_cmd() {
    local cmd="$1"
    local description="${2:-명령어}" # 기본 설명은 "명령어"
    
    log_debug "실행 중: $cmd"
    
    if [[ "$DRY_RUN" == "true" ]]; then
        log_debug "실행 예정: $cmd"
        return 0
    fi
    
    if [[ "$VERBOSE" == "true" ]]; then
        eval "$cmd" # 자세한 출력 모드에서는 명령어 출력을 그대로 표시
    else
        eval "$cmd" >/dev/null # 일반 모드에서는 명령어 출력 숨김
    fi
    
    local exit_code=$?
    if [[ $exit_code -ne 0 ]]; then
        log_error "명령어 실행 실패 (종료 코드 $exit_code): $cmd"
        die "$description 실행 실패" $exit_code
    else
        log_success "$description 성공적으로 실행됨"
    fi
}

# --- 패턴과 일치하는 파일 찾기 ---
find_files() {
    local dir="$1"
    local pattern="$2"
    local max_depth="${3:-1}" # 기본 최대 깊이는 1
    
    find "$dir" -maxdepth "$max_depth" -name "$pattern" -type f 2>/dev/null
}

# --- 패턴과 일치하는 최신 파일 찾기 ---
get_latest_file() {
    local dir="$1"
    local pattern="$2"
    
    find_files "$dir" "$pattern" | sort -r | head -n 1 # 최신 파일을 찾기 위해 역순 정렬 후 첫 번째 항목 가져오기
}

# --- 플랫폼 감지 ---
get_platform() {
    case "$(uname -s)" in
        Darwin*) echo "macos" ;;
        Linux*)  echo "linux" ;;
        CYGWIN*|MINGW*|MSYS*) echo "windows" ;;
        *) echo "unknown" ;;
    esac
}

# --- 특정 플랫폼에서 실행 중인지 확인 ---
is_macos() {
    [[ "$(get_platform)" == "macos" ]]
}

is_linux() {
    [[ "$(get_platform)" == "linux" ]]
}

is_windows() {
    [[ "$(get_platform)" == "windows" ]]
}

# --- 환경 유효성 검사 ---
validate_environment() {
    log_step "환경 유효성 검사 중..."
    
    # 필수 명령어 확인
    local required_commands=("git" "node" "npm")
    for cmd in "${required_commands[@]}"; do
        if ! command_exists "$cmd"; then
            die "필수 명령어 '$cmd'를 찾을 수 없습니다."
        fi
        log_debug "명령어 '$cmd' 찾음"
    done
    
    # Node.js 버전 확인
    local node_version
    node_version=$(node --version | sed 's/v//')
    local required_node_version="16.0.0"
    
    if ! version_gte "$node_version" "$required_node_version"; then
        die "Node.js 버전 $node_version 이 너무 오래되었습니다. 요구 사항: $required_node_version 이상."
    fi
    
    log_success "환경 유효성 검사 통과"
}

# --- 버전 비교 (version1 >= version2) ---
version_gte() {
    local version1="$1"
    local version2="$2"
    
    # 간단한 버전 비교 (대부분의 경우 작동)
    printf '%s\n%s\n' "$version2" "$version1" | sort -V -C
}

# --- 명령줄 인자 파싱 (기본 옵션) ---
parse_args() {
    while [[ $# -gt 0 ]]; do
        case $1 in
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
                # 알 수 없는 옵션
                log_warn "알 수 없는 옵션: $1"
                shift
                ;;
        esac
    done
}

# --- 도움말 표시 (개별 스크립트에서 재정의될 예정) ---
show_help() {
    echo "사용법: $0 [옵션]"
    echo ""
    echo "옵션:"
    echo "  -v, --verbose    자세한 출력 활성화"
    echo "  -n, --dry-run    실행하지 않고 수행될 작업 표시"
    echo "  -h, --help       이 도움말 메시지 표시"
}

# --- 정리 함수 (스크립트 종료 시 호출) ---
cleanup() {
    local exit_code=$?
    if [[ $exit_code -ne 0 ]]; then
        log_error "스크립트가 종료 코드 $exit_code 로 실패했습니다."
    fi
    return $exit_code
}

# --- 정리 함수를 위한 트랩 설정 ---
trap cleanup EXIT

# --- 공통 변수 초기화 (이미 초기화되지 않은 경우에만) ---
if [[ -z "${_INIT_COMMON_CALLED:-}" ]]; then
    # 이 파일을 소싱한 스크립트의 디렉터리를 가져옵니다.
    if [[ -z "${SCRIPT_DIR:-}" ]]; then
        SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[1]}")" && pwd)"
        export SCRIPT_DIR
    fi
    # PROJECT_ROOT는 메인 스크립트에서 설정되어야 하므로 재정의하지 않습니다.
    if [[ -z "${PROJECT_ROOT:-}" ]]; then
        PROJECT_ROOT="$(dirname "$SCRIPT_DIR")"
        export PROJECT_ROOT
    fi
    _INIT_COMMON_CALLED=1
fi
