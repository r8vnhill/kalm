#!/usr/bin/env bash
# KALM Build Environment Setup Script
#
# This script configures the KALM build environment inside a Docker container:
# - Updates package index
# - Installs base dependencies
# - Configures Microsoft PowerShell repository
# - Adds OpenJDK PPA for newer Java versions
# - Installs PowerShell 7.4+ and OpenJDK 22
# - Configures UTF-8 locale for reproducibility
# - Cleans up to reduce image size
#
# Exit on error, undefined variables, and pipe failures
set -euo pipefail

echo "Setting up KALM build environment..."

# Update package index
echo "Updating package index..."
apt-get update

# Install base dependencies
echo "Installing base dependencies..."
DEBIAN_FRONTEND=noninteractive apt-get install -y --no-install-recommends \
    ca-certificates \
    curl \
    wget \
    git \
    locales \
    tzdata \
    build-essential \
    software-properties-common \
    gnupg2

# Configure Microsoft PowerShell repository
echo "Configuring Microsoft PowerShell repository..."
mkdir -p /usr/share/keyrings

# Download Microsoft's GPG key in ASCII-armored format and convert it to binary GPG keyring format.
# The curl flags: --fail (error on HTTP errors), --silent (no progress), --show-error (show errors anyway),
# --location (follow redirects). The gpg --dearmor converts ASCII armor to binary keyring format.
# This key is used to verify the authenticity of packages from Microsoft's Ubuntu repository.
curl --fail --silent --show-error --location https://packages.microsoft.com/keys/microsoft.asc | \
    gpg --dearmor -o /usr/share/keyrings/microsoft-archive-keyring.gpg

cat > /etc/apt/sources.list.d/microsoft.list << EOF
deb [arch=amd64,arm64,armhf signed-by=/usr/share/keyrings/microsoft-archive-keyring.gpg] https://packages.microsoft.com/repos/microsoft-ubuntu-jammy-prod jammy main
EOF

# Add OpenJDK PPA for newer Java versions (Java 22+)
echo "Adding OpenJDK PPA..."
add-apt-repository -y ppa:openjdk-r/ppa

# Update package index after adding repositories
echo "Updating package index after adding repositories..."
apt-get update

# Install PowerShell 7.4+
echo "Installing PowerShell 7.4+..."
DEBIAN_FRONTEND=noninteractive apt-get install -y --no-install-recommends powershell

# Install OpenJDK 22 (matches build-logic DEFAULT_JAVA_VERSION)
echo "Installing OpenJDK 22..."
DEBIAN_FRONTEND=noninteractive apt-get install -y --no-install-recommends openjdk-22-jdk-headless

# Configure UTF-8 locale (important for reproducibility)
echo "Configuring UTF-8 locale..."
echo "en_US.UTF-8 UTF-8" >> /etc/locale.gen
locale-gen en_US.UTF-8

# Clean up to reduce image size
echo "Cleaning up..."
apt-get clean
rm -rf /var/lib/apt/lists/*

echo "KALM build environment setup completed successfully."
