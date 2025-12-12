#!/bin/bash
#
# Download Dependencies Script for MattMC
# 
# This script downloads all Mojang and Fabric dependencies that are NOT available
# on Maven Central to a local directory structure for offline builds.
#
# Usage: ./download-dependencies.sh
# 
# The script will create a libraries/deps/ directory with all required JARs.
# After running this script, you can build MattMC without internet access
# to the blocked repositories (libraries.minecraft.net, maven.fabricmc.net, etc.)
#

set -e  # Exit on error

# Color codes for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Determine script directory
SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
DEPS_DIR="${SCRIPT_DIR}/deps"

echo -e "${BLUE}╔════════════════════════════════════════════════════════════════╗${NC}"
echo -e "${BLUE}║        MattMC Dependency Download Script                      ║${NC}"
echo -e "${BLUE}║                                                                ║${NC}"
echo -e "${BLUE}║  This script downloads dependencies from blocked repos        ║${NC}"
echo -e "${BLUE}╚════════════════════════════════════════════════════════════════╝${NC}"
echo ""

# Create deps directory
echo -e "${YELLOW}📁 Creating dependencies directory...${NC}"
mkdir -p "${DEPS_DIR}"

# Function to download a JAR file
download_jar() {
    local url="$1"
    local filename="$2"
    local filepath="${DEPS_DIR}/${filename}"
    
    if [ -f "${filepath}" ]; then
        echo -e "   ${GREEN}✓${NC} ${filename} (already exists)"
        return 0
    fi
    
    echo -e "   ${YELLOW}⬇${NC} Downloading ${filename}..."
    if curl -L -f -s -o "${filepath}" "${url}"; then
        echo -e "   ${GREEN}✓${NC} ${filename}"
        return 0
    else
        echo -e "   ${RED}✗${NC} Failed to download ${filename}"
        return 1
    fi
}

# Check internet connectivity to blocked repositories
echo -e "${YELLOW}🔍 Checking connectivity to required repositories...${NC}"
REPO_ACCESSIBLE=true

for repo in "libraries.minecraft.net" "maven.fabricmc.net" "gitlab.com"; do
    if ! curl -s -I "https://${repo}" --connect-timeout 5 --max-time 10 > /dev/null 2>&1; then
        echo -e "   ${RED}✗${NC} Cannot reach ${repo}"
        REPO_ACCESSIBLE=false
    else
        echo -e "   ${GREEN}✓${NC} ${repo} is accessible"
    fi
done

if [ "$REPO_ACCESSIBLE" = false ]; then
    echo -e "${RED}╔════════════════════════════════════════════════════════════════╗${NC}"
    echo -e "${RED}║  ERROR: Required repositories are not accessible!             ║${NC}"
    echo -e "${RED}║                                                                ║${NC}"
    echo -e "${RED}║  This script must be run on a machine with unrestricted       ║${NC}"
    echo -e "${RED}║  internet access to download dependencies from:               ║${NC}"
    echo -e "${RED}║  - libraries.minecraft.net                                    ║${NC}"
    echo -e "${RED}║  - maven.fabricmc.net                                         ║${NC}"
    echo -e "${RED}║  - gitlab.com (for Distant Horizons core)                     ║${NC}"
    echo -e "${RED}╚════════════════════════════════════════════════════════════════╝${NC}"
    exit 1
fi

echo ""
echo -e "${GREEN}✓ All required repositories are accessible${NC}"
echo ""

# Download Mojang libraries from libraries.minecraft.net
echo -e "${BLUE}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
echo -e "${BLUE}  Downloading Mojang Libraries (8 dependencies)${NC}"
echo -e "${BLUE}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"

MOJANG_BASE="https://libraries.minecraft.net"

download_jar "${MOJANG_BASE}/com/mojang/brigadier/1.3.10/brigadier-1.3.10.jar" "brigadier-1.3.10.jar"
download_jar "${MOJANG_BASE}/com/mojang/datafixerupper/8.0.16/datafixerupper-8.0.16.jar" "datafixerupper-8.0.16.jar"
download_jar "${MOJANG_BASE}/com/mojang/authlib/6.0.55/authlib-6.0.55.jar" "authlib-6.0.55.jar"
download_jar "${MOJANG_BASE}/com/mojang/logging/1.2.7/logging-1.2.7.jar" "logging-1.2.7.jar"
download_jar "${MOJANG_BASE}/com/mojang/jtracy/1.0.29/jtracy-1.0.29.jar" "jtracy-1.0.29.jar"
download_jar "${MOJANG_BASE}/com/mojang/blocklist/1.0.10/blocklist-1.0.10.jar" "blocklist-1.0.10.jar"
download_jar "${MOJANG_BASE}/com/mojang/patchy/2.2.10/patchy-2.2.10.jar" "patchy-2.2.10.jar"
download_jar "${MOJANG_BASE}/com/mojang/text2speech/1.17.9/text2speech-1.17.9.jar" "text2speech-1.17.9.jar"

echo ""

# ============================================================================
# FABRIC LOADER DEPENDENCIES
# These are required to compile and run the integrated Fabric Loader source
# ============================================================================

echo -e "${BLUE}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
echo -e "${BLUE}  Downloading Fabric Loader Core Dependencies${NC}"
echo -e "${BLUE}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"

FABRIC_BASE="https://maven.fabricmc.net"

# Sponge Mixin - bytecode transformation framework (required by Fabric Loader)
# Provides: org.spongepowered.asm.* packages
download_jar "${FABRIC_BASE}/net/fabricmc/sponge-mixin/0.16.5+mixin.0.8.7/sponge-mixin-0.16.5+mixin.0.8.7.jar" "sponge-mixin-0.16.5+mixin.0.8.7.jar"

# Tiny Remapper - class/method/field remapping (required by Fabric Loader)
# Provides: net.fabricmc.tinyremapper.* packages
download_jar "${FABRIC_BASE}/net/fabricmc/tiny-remapper/0.11.2/tiny-remapper-0.11.2.jar" "tiny-remapper-0.11.2.jar"

# Class Tweaker - class access modification (required by Fabric Loader)
# Provides: net.fabricmc.classtweaker.* packages
download_jar "${FABRIC_BASE}/net/fabricmc/class-tweaker/0.2/class-tweaker-0.2.jar" "class-tweaker-0.2.jar"

# Mapping IO - mapping file I/O (required by Fabric Loader)
# Provides: net.fabricmc.mappingio.* packages
download_jar "${FABRIC_BASE}/net/fabricmc/mapping-io/0.7.1/mapping-io-0.7.1.jar" "mapping-io-0.7.1.jar"

# MixinExtras - additional Mixin functionality (optional but commonly used)
# Provides: com.llamalad7.mixinextras.* packages
download_jar "${FABRIC_BASE}/io/github/llamalad7/mixinextras-fabric/0.5.0/mixinextras-fabric-0.5.0.jar" "mixinextras-fabric-0.5.0.jar"

# Launchwrapper - legacy launch wrapper (required for some Fabric Loader components)
# Provides: net.minecraft.launchwrapper.* packages
download_jar "${MOJANG_BASE}/net/minecraft/launchwrapper/1.12/launchwrapper-1.12.jar" "launchwrapper-1.12.jar"

# Access Widener - access modification (required by Fabric Loader)
download_jar "${FABRIC_BASE}/net/fabricmc/access-widener/2.1.0/access-widener-2.1.0.jar" "access-widener-2.1.0.jar"

# Tiny Mappings Parser - legacy mapping parser
download_jar "${FABRIC_BASE}/net/fabricmc/tiny-mappings-parser/0.3.0+build.17/tiny-mappings-parser-0.3.0+build.17.jar" "tiny-mappings-parser-0.3.0+build.17.jar"

echo ""

# ============================================================================
# ASM DEPENDENCIES
# Bytecode manipulation library used by Fabric Loader and Mixin
# ============================================================================

echo -e "${BLUE}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
echo -e "${BLUE}  Downloading ASM Dependencies (from Fabric Maven)${NC}"
echo -e "${BLUE}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"

# ASM 9.9 (version required by Fabric Loader 0.18.2)
# Note: Fabric hosts ASM on their Maven to ensure version consistency
download_jar "${FABRIC_BASE}/org/ow2/asm/asm/9.9/asm-9.9.jar" "asm-9.9.jar"
download_jar "${FABRIC_BASE}/org/ow2/asm/asm-analysis/9.9/asm-analysis-9.9.jar" "asm-analysis-9.9.jar"
download_jar "${FABRIC_BASE}/org/ow2/asm/asm-commons/9.9/asm-commons-9.9.jar" "asm-commons-9.9.jar"
download_jar "${FABRIC_BASE}/org/ow2/asm/asm-tree/9.9/asm-tree-9.9.jar" "asm-tree-9.9.jar"
download_jar "${FABRIC_BASE}/org/ow2/asm/asm-util/9.9/asm-util-9.9.jar" "asm-util-9.9.jar"

echo ""

# Note: Fabric API is not downloaded as JARs because they use intermediary mappings.
# Instead, we use Mojang-mapped stub interfaces in src/main/java/net/fabricmc/fabric/api/

# ============================================================================
# DISTANT HORIZONS CORE SUBMODULE
# Downloads the coreSubProjects git submodule required for DH compilation
# ============================================================================

echo -e "${BLUE}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
echo -e "${BLUE}  Downloading Distant Horizons Core Submodule${NC}"
echo -e "${BLUE}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"

DH_DIR="${SCRIPT_DIR}/../modules/distant-horizons-2.3.4b"
DH_CORE_DIR="${DH_DIR}/coreSubProjects"

if [ -d "${DH_CORE_DIR}" ] && [ -f "${DH_CORE_DIR}/LICENSE.txt" ]; then
    echo -e "   ${GREEN}✓${NC} Distant Horizons coreSubProjects (already exists)"
else
    echo -e "   ${YELLOW}⬇${NC} Cloning coreSubProjects from GitLab..."
    if git clone --depth 1 https://gitlab.com/jeseibel/distant-horizons-core.git "${DH_CORE_DIR}" 2>/dev/null; then
        echo -e "   ${GREEN}✓${NC} Distant Horizons coreSubProjects"
    else
        echo -e "   ${RED}✗${NC} Failed to clone coreSubProjects"
        echo -e "   ${YELLOW}⚠${NC} Please manually clone the repository:"
        echo -e "   ${YELLOW}   cd modules/distant-horizons-2.3.4b${NC}"
        echo -e "   ${YELLOW}   git clone https://gitlab.com/jeseibel/distant-horizons-core.git coreSubProjects${NC}"
    fi
fi

echo ""

# ============================================================================
# DISTANT HORIZONS DEPENDENCIES
# Additional dependencies required by Distant Horizons
# ============================================================================

echo -e "${BLUE}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
echo -e "${BLUE}  Downloading Distant Horizons Dependencies${NC}"
echo -e "${BLUE}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"

MAVEN_CENTRAL="https://repo1.maven.org/maven2"

# NightConfig - TOML and JSON configuration library (required by DH)
download_jar "${MAVEN_CENTRAL}/com/electronwill/night-config/toml/3.6.6/toml-3.6.6.jar" "nightconfig-toml-3.6.6.jar"
download_jar "${MAVEN_CENTRAL}/com/electronwill/night-config/json/3.6.6/json-3.6.6.jar" "nightconfig-json-3.6.6.jar"
download_jar "${MAVEN_CENTRAL}/com/electronwill/night-config/core/3.6.6/core-3.6.6.jar" "nightconfig-core-3.6.6.jar"

# SQLite JDBC - Database driver (required by DH for LOD storage)
download_jar "${MAVEN_CENTRAL}/org/xerial/sqlite-jdbc/3.47.2.0/sqlite-jdbc-3.47.2.0.jar" "sqlite-jdbc-3.47.2.0.jar"

# XZ compression library (required by DH)
download_jar "${MAVEN_CENTRAL}/org/tukaani/xz/1.9/xz-1.9.jar" "xz-1.9.jar"

echo ""

# Summary
echo -e "${GREEN}╔════════════════════════════════════════════════════════════════╗${NC}"
echo -e "${GREEN}║  ✓ Download Complete!                                         ║${NC}"
echo -e "${GREEN}╚════════════════════════════════════════════════════════════════╝${NC}"
echo ""
echo -e "Dependencies saved to: ${DEPS_DIR}"
echo ""
echo -e "Total JARs downloaded: $(ls -1 "${DEPS_DIR}"/*.jar 2>/dev/null | wc -l)"
echo ""
echo -e "${YELLOW}Next steps:${NC}"
echo -e "  1. The build.gradle has been configured to use these bundled dependencies"
echo -e "  2. Run: ./gradlew build --offline"
echo -e "  3. The build will use only the local dependencies from libraries/deps/"
echo ""
echo -e "${GREEN}You can now build MattMC without access to blocked repositories!${NC}"
