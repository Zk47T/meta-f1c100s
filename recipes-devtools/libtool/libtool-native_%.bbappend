# Override do_compile to skip the .inc's do_compile:prepend
# which deletes ltmain.sh and tries 'make build-aux/ltmain.sh'.
# With --exclude=libtoolize there's no Makefile rule for it.
# The tarball ships ltmain.sh so just run make directly.
do_compile () {
	oe_runmake
}
