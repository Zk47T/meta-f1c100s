DESCRIPTION="Upstream's U-boot configured for sunxi devices"

require recipes-bsp/u-boot/u-boot.inc

DEPENDS += " bc-native dtc-native swig-native python3-native "

LICENSE = "GPLv2+"
LIC_FILES_CHKSUM = "file://Licenses/README;md5=a2c678cfd4a4d97135585cad908541c6"

COMPATIBLE_MACHINE = "(f1c100s)"

DEFAULT_PREFERENCE:f1c100s = "1"

SRCREV="${AUTOREV}"
SRCREV_machine="${AUTOREV}"

SRC_URI += "git://github.com/ninhnn2/u-boot-f1c100s.git;protocol=https \
	    file://boot.cmd \
	    file://0001-pylibfdt-fix-version-for-python3.12-and-yylloc.patch \
	   "

S = "${WORKDIR}/git"
B = "${S}"

UBOOT_ENV_SUFFIX = "scr"
UBOOT_ENV = "boot"
UBOOT_INITIAL_ENV = ""

EXTRA_OEMAKE += ' HOSTLDSHARED="${BUILD_CC} -shared ${BUILD_LDFLAGS} ${BUILD_CFLAGS}" '

do_configure:prepend() {
    # Allow building when source tree has .config (in-tree build with O= set)
    sed -i '/is not clean/,/fi;/{s|/bin/false|/bin/true|}' ${S}/Makefile
    # Add -fcommon to HOSTCFLAGS directly (GCC 10+ fix for yylloc multiple definition)
    grep -q "fcommon" ${S}/Makefile || \
        sed -i 's/-fomit-frame-pointer/-fomit-frame-pointer -fcommon/' ${S}/Makefile
    # Fix make 4.3+: \# in recipes is no longer stripped to #
    # Replace echo '\#include' with printf '\043include' (octal for #)
    cat > ${S}/.fix_uboot.py << 'PYEOF'
import sys

# Fix 1: Makefile.lib DTS recipe \# issue
fname = sys.argv[1] + "/scripts/Makefile.lib"
with open(fname) as f:
    content = f.read()
old = "echo '\\#include \"$(u_boot_dtsi)\"'"
new = "printf '\\043include \"%s\"\\n' \"$(u_boot_dtsi)\""
content = content.replace(old, new)
with open(fname, 'w') as f:
    f.write(content)

# Fix 2: Replace binman (requires python2) with objcopy+cat for sunxi
fname2 = sys.argv[1] + "/Makefile"
with open(fname2) as f:
    content2 = f.read()
old2 = ("u-boot-sunxi-with-spl.bin: spl/sunxi-spl.bin u-boot.img u-boot.dtb FORCE\n"
        "\t$(call if_changed,binman)")
new2 = ("u-boot-sunxi-with-spl.bin: spl/sunxi-spl.bin u-boot.img u-boot.dtb FORCE\n"
        "\t$(OBJCOPY) -I binary -O binary --gap-fill=0xff --pad-to=$(CONFIG_SPL_PAD_TO)"
        " spl/sunxi-spl.bin u-boot-sunxi-spl.tmp\n"
        "\tcat u-boot-sunxi-spl.tmp u-boot.img > $@\n"
        "\trm -f u-boot-sunxi-spl.tmp")
content2 = content2.replace(old2, new2)
with open(fname2, 'w') as f:
    f.write(content2)
PYEOF
    python3 ${S}/.fix_uboot.py ${S}
    rm -f ${S}/.fix_uboot.py
}

do_compile:append() {
    ${S}/tools/mkimage -C none -A arm -T script -d ${WORKDIR}/boot.cmd ${WORKDIR}/boot.scr
}
