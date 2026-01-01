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

for repo in "libraries.minecraft.net" "maven.fabricmc.net"; do
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
    echo -e "${RED}╚════════════════════════════════════════════════════════════════╝${NC}"
    exit 1
fi

echo ""
echo -e "${GREEN}✓ All required repositories are accessible${NC}"
echo ""

# Download Mojang libraries from libraries.minecraft.net
echo -e "${BLUE}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
echo -e "${BLUE}  Downloading Mojang Libraries (5 dependencies)${NC}"
echo -e "${BLUE}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"

MOJANG_BASE="https://libraries.minecraft.net"

download_jar "${MOJANG_BASE}/com/mojang/brigadier/1.3.10/brigadier-1.3.10.jar" "brigadier-1.3.10.jar"
download_jar "${MOJANG_BASE}/com/mojang/datafixerupper/8.0.16/datafixerupper-8.0.16.jar" "datafixerupper-8.0.16.jar"
# NOTE: authlib removed - replaced with custom PlayerProfile system (net.minecraft.server.profile.PlayerProfile, ProfileProperty, ProfilePropertyMap)
download_jar "${MOJANG_BASE}/com/mojang/logging/1.2.7/logging-1.2.7.jar" "logging-1.2.7.jar"
download_jar "${MOJANG_BASE}/com/mojang/jtracy/1.0.29/jtracy-1.0.29.jar" "jtracy-1.0.29.jar"
# NOTE: blocklist removed - replaced with ALLOW_ALL AddressCheck (no server blocking in dev builds)
# NOTE: patchy removed - only contained blocklist functionality
# NOTE: text2speech removed - not used (only translation keys like "narrator.*" are referenced)

echo ""

# ============================================================================
# FABRIC LOADER DEPENDENCIES
# These are required to compile and run the integrated Fabric Loader source
# ============================================================================

echo -e "${BLUE}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
echo -e "${BLUE}  Downloading Fabric Loader Core Dependencies${NC}"
echo -e "${BLUE}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"

FABRIC_BASE="https://maven.fabricmc.net"

# NOTE: Sponge Mixin removed - mixin system completely bypassed, all mixins converted to hooks

# NOTE: Tiny Remapper removed - unified build approach means all code uses
# consistent mappings at compile time, making runtime remapping unnecessary

# NOTE: Class Tweaker removed - access modifications already applied in source

# Mapping IO - mapping file I/O (required by Fabric Loader)
# Provides: net.fabricmc.mappingio.* packages
download_jar "${FABRIC_BASE}/net/fabricmc/mapping-io/0.7.1/mapping-io-0.7.1.jar" "mapping-io-0.7.1.jar"

# NOTE: MixinExtras removed - mixin system completely bypassed, all mixins converted to hooks

# NOTE: Launchwrapper removed - legacy code removed from Fabric Loader

# NOTE: Access Widener removed - access modifications already applied in source

# NOTE: Tiny Mappings Parser removed - superseded by mapping-io with built-in Tiny v1 support

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
