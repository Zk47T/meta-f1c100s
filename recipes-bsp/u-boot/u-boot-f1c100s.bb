DESCRIPTION="Upstream's U-boot configured for sunxi devices"

require recipes-bsp/u-boot/u-boot.inc

DEPENDS += " bc-native dtc-native swig-native python3-native "

LICENSE = "GPLv2+"
LIC_FILES_CHKSUM = "file://Licenses/README;md5=a2c678cfd4a4d97135585cad908541c6"

COMPATIBLE_MACHINE = "(f1c100s)"

DEFAULT_PREFERENCE:f1c100s = "1"

SRCREV="${AUTOREV}"
SRCREV_machine="${AUTOREV}"

# patches merged into u-boot-f1c100s fork — no longer needed here
SRC_URI += "git://github.com/Zk47T/u-boot-f1c100s.git;protocol=https \
	    file://boot.cmd \
	   "

S = "${WORKDIR}/git"
B = "${S}"

UBOOT_ENV_SUFFIX = "scr"
UBOOT_ENV = "boot"
UBOOT_INITIAL_ENV = ""

EXTRA_OEMAKE += ' HOSTLDSHARED="${BUILD_CC} -shared ${BUILD_LDFLAGS} ${BUILD_CFLAGS}" '

do_configure:prepend() {
    # Allow building when source tree has .config (Yocto in-tree build workaround)
    sed -i '/is not clean/,/fi;/{s|/bin/false|/bin/true|}' ${S}/Makefile
}

do_compile:append() {
    ${S}/tools/mkimage -C none -A arm -T script -d ${WORKDIR}/boot.cmd ${WORKDIR}/boot.scr
}
