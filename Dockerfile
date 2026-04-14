FROM ubuntu:20.04

ENV DEBIAN_FRONTEND=noninteractive
ENV LANG=en_US.UTF-8

# Yocto Zeus build dependencies
RUN apt-get update && apt-get install -y \
    gawk wget git diffstat unzip texinfo \
    gcc g++ build-essential chrpath socat cpio \
    python3 python3-pip python3-pexpect python3-git python3-jinja2 \
    python3-dev python3-setuptools swig \
    python2 python2-dev \
    libtool autoconf automake \
    xz-utils debianutils iputils-ping \
    libsdl1.2-dev xterm \
    file lz4 zstd \
    locales sudo \
    && rm -rf /var/lib/apt/lists/*

# Locale setup (required by bitbake)
RUN locale-gen en_US.UTF-8
# u-boot 2018.01 uses Python 2 — python2-dev provides the headers for pylibfdt

# Create non-root user (bitbake refuses to run as root)
RUN useradd -m -s /bin/bash builder && \
    echo "builder ALL=(ALL) NOPASSWD:ALL" >> /etc/sudoers

USER builder
WORKDIR /home/builder

CMD ["/bin/bash"]
