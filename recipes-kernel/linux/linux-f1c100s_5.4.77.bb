require recipes-kernel/linux/linux-f1c100s.inc
DESCRIPTION = "Linux kernel for F1C100s"
KERNEL_IMAGETYPE = "zImage"

COMPATIBLE_MACHINE = "(f1c100s)"

KERNEL_DEVICETREE = " \
    suniv-f1c100s-licheepi-nano.dtb \
"

PV = "5.4.77"
PR = "r0"

SRCREV_pn-${PN} = "${AUTOREV}"

FILESEXTRAPATHS_prepend := "${THISDIR}/linux-f1c100s_5.4.77:"
SRC_URI += "git://github.com/florpor/linux.git;protocol=https;branch=licheepi-nano-v5.4.y"
SRC_URI += "file://f1c100s_defconfig"

do_configure_prepend() {
    cp ${WORKDIR}/f1c100s_defconfig ${S}/arch/arm/configs/f1c100s_defconfig
}

S = "${WORKDIR}/git/"
LDFLAGS = ""
TARGET_LDFLAGS = ""
B = "${S}"
