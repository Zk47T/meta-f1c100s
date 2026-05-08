FILESEXTRAPATHS:prepend := "${THISDIR}/files:"

SRC_URI += "file://rnet"

do_install:append() {
    install -m 0644 ${WORKDIR}/rnet ${D}${sysconfdir}/ppp/peers
}
