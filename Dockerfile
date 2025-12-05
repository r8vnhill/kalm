# KALM Base Build & Experiments Environment
#
# This Dockerfile creates a reproducible Linux environment for KALM builds, tests, and scientific
# experiments. It is intended for:
# - CI/CD pipelines (GitLab, etc.)
# - Local development with container isolation
# - Scientific reproducibility (experiments run in the same image as CI)
#
# The image includes:
# - Linux base (Ubuntu 22.04 LTS) with security updates
# - PowerShell 7.4+ for script automation
# - JDK (matching the project requirements)
# - Git and build essentials
# - Locale support for consistent text processing
#
# The source code is NOT baked into the image; mount or copy the repo at runtime.
# Always use ./gradlew (the Gradle wrapper) from the mounted repo; do not install standalone Gradle
# into the image.

FROM ubuntu:22.04

# Set metadata using OCI Image Spec standard keys (org.opencontainers.image.*)
# This enables standardized tooling, CI/CD integration, and image management
LABEL maintainer="Ignacio Slater-Muñoz <https://ravenhill.cl>" \
    org.opencontainers.image.title="KALM Base Build & Experiments Environment" \
    org.opencontainers.image.description="Reproducible Linux environment for KALM builds, tests, and scientific experiments" \
    org.opencontainers.image.version="0.1.0" \
    org.opencontainers.image.source="https://gitlab.com/r8vnhill/kalm" \
    org.opencontainers.image.documentation="https://gitlab.com/r8vnhill/kalm" \
    org.opencontainers.image.base.name="ubuntu:22.04" \
    org.opencontainers.image.vendor="KALM Contributors"

# Install system dependencies and configure locale
# Use a single RUN command to reduce layer count.
RUN set -eux; \
    apt-get update; \
    DEBIAN_FRONTEND=noninteractive apt-get install -y --no-install-recommends \
        ca-certificates \
        curl \
        wget \
        git \
        locales \
        tzdata \
        build-essential \
        software-properties-common \
        gnupg2; \
    \
    # Add Microsoft's PowerShell repository (modern method)
    mkdir -p /usr/share/keyrings; \
    curl -fsSL https://packages.microsoft.com/keys/microsoft.asc | gpg --dearmor -o /usr/share/keyrings/microsoft-archive-keyring.gpg; \
    echo "deb [arch=amd64,arm64,armhf signed-by=/usr/share/keyrings/microsoft-archive-keyring.gpg] https://packages.microsoft.com/repos/microsoft-ubuntu-jammy-prod jammy main" > /etc/apt/sources.list.d/microsoft.list; \
    \
    # Add PPA for newer OpenJDK versions (Java 22+)
    add-apt-repository -y ppa:openjdk-r/ppa; \
    \
    # Install PowerShell 7.4+
    apt-get update; \
    DEBIAN_FRONTEND=noninteractive apt-get install -y --no-install-recommends powershell; \
    \
    # Install OpenJDK 22 (matches build-logic DEFAULT_JAVA_VERSION)
    DEBIAN_FRONTEND=noninteractive apt-get install -y --no-install-recommends openjdk-22-jdk-headless; \
    \
    # Configure locale to UTF-8 (important for reproducibility)
    echo "en_US.UTF-8 UTF-8" >> /etc/locale.gen; \
    locale-gen en_US.UTF-8; \
    \
    # Clean up to reduce image size
    apt-get clean; \
    rm -rf /var/lib/apt/lists/*

# Set environment variables for Java and locale
ENV LANG=en_US.UTF-8 \
    LC_ALL=en_US.UTF-8 \
    JAVA_HOME=/usr/lib/jvm/java-22-openjdk-amd64 \
    GRADLE_USER_HOME=/gradle-cache \
    GRADLE_OPTS="-Dorg.gradle.daemon=false"

# Ensure JAVA_HOME is in PATH
ENV PATH=$JAVA_HOME/bin:$PATH

# Create a non-root user for builds (optional but recommended for security)
RUN useradd -m -s /usr/bin/pwsh builder

# Create directories for workspace and gradle cache
RUN mkdir -p /workspace /gradle-cache && \
    chown -R builder:builder /workspace /gradle-cache

# Set working directory
WORKDIR /workspace

# Switch to non-root user (optional; comment out if you prefer root)
# USER builder

# Set bash as the default shell/entrypoint for the container
# This allows ./gradlew and shell scripts to work by default.
# Users can still invoke pwsh explicitly: docker run ... pwsh -Command "..."
SHELL ["/bin/bash", "-c"]
ENTRYPOINT ["/bin/bash"]
CMD ["-i"]

# Health check: verify Java and PowerShell are available
HEALTHCHECK --interval=30s --timeout=10s --start-period=5s --retries=3 \
    CMD java -version && pwsh -Version
ENTRYPOINT ["/usr/bin/pwsh", "-NoProfile"]
