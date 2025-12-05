# KALM Base Build & Experiments Environment
#
# This Dockerfile creates a reproducible Linux environment for KALM builds, tests,
# and scientific experiments. It is intended for:
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
# Always use ./gradlew (the Gradle wrapper) from the mounted repo; do not install
# standalone Gradle into the image.

FROM ubuntu:22.04

# Set metadata
LABEL maintainer="KALM Contributors <https://github.com/r8vnhill/kalm>"
LABEL description="KALM Base Build & Experiments Environment"
LABEL version="0.1.0"

# Install system dependencies and configure locale
# Use a single RUN command to reduce layer count.
RUN set -eux; \
    apt-get update; \
    apt-get install -y --no-install-recommends \
        ca-certificates \
        curl \
        git \
        locales \
        tzdata \
        build-essential \
        software-properties-common; \
    \
    # Add Microsoft's PowerShell repository
    curl https://packages.microsoft.com/keys/microsoft.asc | apt-key add -; \
    echo "deb [arch=amd64,arm64,armhf signed-by=/usr/share/keyrings/microsoft.gpg] https://packages.microsoft.com/repos/microsoft-ubuntu-jammy main" > /etc/apt/sources.list.d/microsoft.list; \
    apt-key adv --keyserver keyserver.ubuntu.com --recv-keys BC528686B50D79E339D3721CEB3E94ADBE1229CF; \
    \
    # Install PowerShell 7.4+
    apt-get update; \
    apt-get install -y --no-install-recommends powershell; \
    \
    # Install OpenJDK 21 (LTS, matches modern Kotlin/Gradle requirements)
    apt-get install -y --no-install-recommends openjdk-21-jdk-headless; \
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
    JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 \
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

# Health check: verify Java and PowerShell are available
HEALTHCHECK --interval=30s --timeout=10s --start-period=5s --retries=3 \
    CMD java -version && pwsh -Version

# Default shell is PowerShell (can override at runtime)
ENTRYPOINT ["/usr/bin/pwsh", "-NoProfile"]
