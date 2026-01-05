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
export DEBIAN_FRONTEND=noninteractive

printf "Setting up KALM build environment...\n"

# Update package index
printf "Updating package index...\n"
apt-get update

# Install base dependencies
printf "Installing base dependencies...\n"
apt-get install -y --no-install-recommends \
	ca-certificates \
	curl \
	wget \
	git \
	locales \
	tzdata \
	build-essential \
	software-properties-common \
	gnupg

# Configure Microsoft PowerShell repository
printf "Configuring Microsoft PowerShell repository...\n"
mkdir -p /usr/share/keyrings

# Download Microsoft's GPG key in ASCII-armored format and convert it to binary GPG keyring format.
# The curl flags: --fail (error on HTTP errors), --silent (no progress), --show-error (show errors anyway),
# --location (follow redirects). The gpg --dearmor converts ASCII armor to binary keyring format.
# This key is used to verify the authenticity of packages from Microsoft's Ubuntu repository.
curl --fail --silent --show-error --location --retry 3 --retry-connrefused --retry-delay 2 \
	https://packages.microsoft.com/keys/microsoft.asc |
	gpg --dearmor --batch --yes -o /usr/share/keyrings/microsoft-archive-keyring.gpg

# Set permissions to be readable by all users
# user: read, write; group: read; others: read
chmod u=rw,g=r,o=r /usr/share/keyrings/microsoft-archive-keyring.gpg

# Write the Microsoft repository source list entry using printf
printf '%s%s\n' \
	'deb [arch=amd64,arm64,armhf signed-by=/usr/share/keyrings/microsoft-archive-keyring.gpg] ' \
	'https://packages.microsoft.com/repos/microsoft-ubuntu-jammy-prod jammy main' \
	>/etc/apt/sources.list.d/microsoft.list

# Add OpenJDK PPA for newer Java versions (Java 22+).
# add-apt-repository is provided by software-properties-common.
printf "Adding OpenJDK PPA...\n"
add-apt-repository -y ppa:openjdk-r/ppa

# Update package index after adding repositories
printf "Updating package index after adding repositories...\n"
apt-get update

# Install PowerShell 7.4+ and OpenJDK 22 (matches build-logic DEFAULT_JAVA_VERSION)
printf "Installing PowerShell 7.4+ and OpenJDK 22...\n"
apt-get install -y --no-install-recommends \
	powershell \
	openjdk-22-jdk-headless

# Configure UTF-8 locale (important for reproducibility)
printf "Configuring UTF-8 locale...\n"
# -q - **quiet mode**: no output; exit status indicates match presence
# -x - require a line to **match the entire pattern**, rather than a substring
# -F - treat patterns as **fixed strings** (no regular expression interpretation)
if ! grep -qxF "en_US.UTF-8 UTF-8" /etc/locale.gen; then
	printf "en_US.UTF-8 UTF-8\n" >>/etc/locale.gen
fi
locale-gen en_US.UTF-8

# Clean up to reduce image size
printf "Cleaning up...\n"
apt-get clean
rm -rf /var/lib/apt/lists/*

printf "KALM build environment setup completed successfully.\n"
