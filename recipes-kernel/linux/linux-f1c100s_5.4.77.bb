require recipes-kernel/linux/linux-f1c100s.inc
DESCRIPTION = "Linux kernel for F1C100s"
KERNEL_IMAGETYPE = "zImage"

COMPATIBLE_MACHINE = "(f1c100s)"

# Set DEVICE_DRIVER = "1" in local.conf to switch to device-driver build
DEVICE_DRIVER ?= "0"

KERNEL_DEVICETREE = "${@'device-driver.dtb' if d.getVar('DEVICE_DRIVER') == '1' else 'suniv-f1c100s-licheepi-nano.dtb'}"

PV = "5.4.77"
PR = "r0"

SRCREV:pn-${PN} = "${AUTOREV}"

FILESEXTRAPATHS:prepend := "${THISDIR}/linux-f1c100s_5.4.77:"
SRC_URI += "git://github.com/florpor/linux.git;protocol=https;branch=licheepi-nano-v5.4.y"
SRC_URI += "file://f1c100s_defconfig"
SRC_URI += "file://device-driver.defconfig"
SRC_URI += "file://dts/device-driver.dts"
SRC_URI += "file://0001-arm-asm-fix-hash-to-percent-for-binutils-2.35.patch"

do_configure:prepend() {
    if [ "${DEVICE_DRIVER}" = "1" ]; then
        bbnote "Device Driver mode: using device-driver.defconfig + device-driver.dts"
        cp ${WORKDIR}/device-driver.defconfig ${S}/arch/arm/configs/f1c100s_defconfig
        cp ${WORKDIR}/dts/device-driver.dts ${S}/arch/arm/boot/dts/device-driver.dts
    else
        cp ${WORKDIR}/f1c100s_defconfig ${S}/arch/arm/configs/f1c100s_defconfig
    fi
}

S = "${WORKDIR}/git"
LDFLAGS = ""
TARGET_LDFLAGS = ""
B = "${S}"
