# Lichee Pi Nano (F1C100S) — Device Driver Development Guide

CPU: Allwinner F1C100S — ARM926EJ-S (ARMv5TE)
Kernel: Linux 5.4.77 | Console: UART0 PE0/PE1 115200 8N1

---

## 1. Host Requirements

```bash
sudo apt update
sudo apt install -y \
    build-essential git bc flex bison \
    libssl-dev libgnutls28-dev \
    gcc-arm-linux-gnueabi \
    dosfstools u-boot-tools
```

Set these once in every terminal you use:

```bash
export ARCH=arm
export CROSS_COMPILE=arm-linux-gnueabi-
```

---

## 2. Bootloader: U-Boot

```bash
git clone https://github.com/Zk47T/u-boot-f1c100s.git
make -C u-boot-f1c100s f1c100s_defconfig
make -C u-boot-f1c100s -j$(nproc)
```

Output: `u-boot-f1c100s/u-boot-sunxi-with-spl.bin`

---

## 3. Linux Kernel

```bash
git clone -b licheepi-nano-v5.4.y https://github.com/Zk47T/linux.git
make -C linux f1c100s_defconfig
make -C linux -j$(nproc) zImage arch/arm/boot/dts/suniv-f1c100s-licheepi-nano.dtb
```

Outputs:
- `linux/arch/arm/boot/zImage`
- `linux/arch/arm/boot/dts/suniv-f1c100s-licheepi-nano.dtb`

---

## 4. Boot Script

```bash
cat > boot.cmd << 'EOF'
setenv bootargs console=ttyS0,115200 rootwait root=/dev/mmcblk0p2 rw
load mmc 0:1 0x80C00000 suniv-f1c100s-licheepi-nano.dtb
load mmc 0:1 0x80008000 zImage
bootz 0x80008000 - 0x80C00000
EOF

mkimage -C none -A arm -T script -d boot.cmd boot.scr
```

---

## 5. Root Filesystem

```bash
wget https://github.com/Zk47T/meta-f1c100s/releases/download/scarthgap-latest/rootfs.tar.xz
```

---

## 6. Flash the SD Card

Find your SD card with `lsblk`. Replace `/dev/sdX` below. Unmount all partitions first.

### Partition

```bash
sudo fdisk /dev/sdX
```

```
o            ← new MBR partition table
n p 1        ← partition 1
[Enter]      ← default first sector
+64M         ← 64 MB boot
t  b         ← type FAT32
n p 2        ← partition 2
[Enter]
[Enter]      ← rest of disk
w            ← write
```

### Format

```bash
sudo mkfs.vfat -n boot   /dev/sdX1
sudo mkfs.ext4 -L rootfs /dev/sdX2
```

### Write U-Boot

```bash
sudo dd if=u-boot-f1c100s/u-boot-sunxi-with-spl.bin of=/dev/sdX bs=1k seek=8 conv=notrunc
```

### Copy Boot Files

```bash
sudo mount /dev/sdX1 /mnt
sudo cp linux/arch/arm/boot/zImage                                  /mnt/
sudo cp linux/arch/arm/boot/dts/suniv-f1c100s-licheepi-nano.dtb    /mnt/
sudo cp boot.scr                                                     /mnt/
sudo umount /mnt
```

### Copy Root Filesystem

```bash
sudo mount /dev/sdX2 /mnt
sudo tar xfp rootfs.tar.xz -C /mnt/
sudo umount /mnt && sync
```

---

## 7. Boot

Connect USB-to-TTL: **TX→PE0, RX→PE1, GND→GND** (3.3 V only — never 5 V).

```bash
screen /dev/ttyUSB0 115200
```

Insert SD card, power on. Login: `root` (no password).

---

## 8. Device Driver Tasks

### Task 1 — LED

```bash
# on the board
echo 1 > /sys/class/leds/licheepi\:green\:user/brightness   # on
echo 0 > /sys/class/leds/licheepi\:green\:user/brightness   # off
echo heartbeat > /sys/class/leds/licheepi\:green\:user/trigger
```

DTS node (PE6):
```dts
leds {
    compatible = "gpio-leds";
    led0 {
        label = "licheepi:green:user";
        gpios = <&pio 4 6 GPIO_ACTIVE_HIGH>;
    };
};
```

---

### Task 2 — GPIO

GPIO line = port × 32 + pin offset. (A=0, B=1, C=2, D=3, E=4)

| Pin  | Line | Note |
|------|------|------|
| PD0  | 96   | I2C0 SDA |
| PD12 | 108  | I2C0 SCK |
| PE4  | 132  | Button |
| PE5  | 133  | free |
| PE6  | 134  | LED — use sysfs, not gpioset |

```bash
gpioinfo                  # list all lines and owners
gpioget gpiochip0 133     # read PE5
gpioset gpiochip0 133=1   # drive high
gpioset gpiochip0 133=0   # drive low
```

---

### Task 3 — Button

Wire: **momentary switch between PE4 and GND**.

```bash
cat /dev/input/event0 | hexdump -C    # press button, watch output
```

Cross-compile a reader on your host:

```c
// btn.c
#include <stdio.h>
#include <fcntl.h>
#include <linux/input.h>
int main(void) {
    struct input_event ev;
    int fd = open("/dev/input/event0", O_RDONLY);
    while (read(fd, &ev, sizeof ev) == sizeof ev)
        if (ev.type == EV_KEY)
            printf("key %d %s\n", ev.code, ev.value ? "down" : "up");
}
```

```bash
${CROSS_COMPILE}gcc -o btn btn.c
scp btn root@<board-ip>:/tmp/
```

---

### Task 4 — I2C

Connect SSD1306: **SDA→PD0, SCL→PD12**.

```bash
i2cdetect -y 0               # scan — expect 0x3c
i2cget  -y 0 0x3c 0x00       # read register
i2cset  -y 0 0x3c 0x00 0xAE  # write: display off
```

---

### Task 5 — SPI

Connect ILI9341: **CLK→PE9, MOSI→PE8, CS→PE7, MISO→PE10**.

Cross-compile on host, short MOSI to MISO for loopback test:

```c
// spi.c
#include <fcntl.h>
#include <sys/ioctl.h>
#include <linux/spi/spidev.h>
#include <stdint.h>
#include <stdio.h>
int main(void) {
    int fd = open("/dev/spidev1.0", O_RDWR);
    uint8_t mode = SPI_MODE_0, bits = 8;
    uint32_t speed = 1000000;
    ioctl(fd, SPI_IOC_WR_MODE, &mode);
    ioctl(fd, SPI_IOC_WR_BITS_PER_WORD, &bits);
    ioctl(fd, SPI_IOC_WR_MAX_SPEED_HZ, &speed);
    uint8_t tx[] = {0xAA, 0xBB, 0xCC, 0xDD}, rx[4] = {};
    struct spi_ioc_transfer tr = {
        .tx_buf = (unsigned long)tx, .rx_buf = (unsigned long)rx,
        .len = 4, .speed_hz = speed, .bits_per_word = 8,
    };
    ioctl(fd, SPI_IOC_MESSAGE(1), &tr);
    printf("rx: %02x %02x %02x %02x\n", rx[0], rx[1], rx[2], rx[3]);
}
```

```bash
${CROSS_COMPILE}gcc -o spi spi.c
scp spi root@<board-ip>:/tmp/
```

---

### Task 6 — Kernel Module

```c
// hello.c
#include <linux/module.h>
#include <linux/miscdevice.h>
#include <linux/fs.h>
static int hello_open(struct inode *i, struct file *f) {
    pr_info("hello: opened\n");
    return 0;
}
static const struct file_operations fops = { .owner = THIS_MODULE, .open = hello_open };
static struct miscdevice dev = { MISC_DYNAMIC_MINOR, "hello", &fops };
static int __init m_init(void) { return misc_register(&dev); }
static void __exit m_exit(void) { misc_deregister(&dev); }
module_init(m_init); module_exit(m_exit);
MODULE_LICENSE("GPL");
```

```makefile
# Makefile
KDIR := ../linux
obj-m := hello.o
all:
	$(MAKE) -C $(KDIR) M=$(PWD) modules
clean:
	$(MAKE) -C $(KDIR) M=$(PWD) clean
```

```bash
make

scp hello.ko root@<board-ip>:/tmp/
# on the board:
insmod /tmp/hello.ko
dmesg | tail -3
rmmod hello
```

---

## 9. Modify the Device Tree

Edit the board DTS directly in the kernel tree:

```bash
$EDITOR linux/arch/arm/boot/dts/suniv-f1c100s-licheepi-nano.dts
```

Rebuild DTB only (seconds, not minutes):

```bash
make -C linux arch/arm/boot/dts/suniv-f1c100s-licheepi-nano.dtb
```

Deploy to the board:

```bash
# option A — over network
scp linux/arch/arm/boot/dts/suniv-f1c100s-licheepi-nano.dtb \
    root@<board-ip>:/boot/suniv-f1c100s-licheepi-nano.dtb
ssh root@<board-ip> reboot

# option B — SD card
sudo mount /dev/sdX1 /mnt
sudo cp linux/arch/arm/boot/dts/suniv-f1c100s-licheepi-nano.dtb /mnt/
sudo umount /mnt
```

---

## Pin Reference

| Pin  | GPIO Line | Used by        |
|------|-----------|----------------|
| PE3  | 131       | ILI9341 RST    |
| PE4  | 132       | Button         |
| PE5  | 133       | ILI9341 DC     |
| PE6  | 134       | LED            |
| PE7  | 135       | SPI1 CS        |
| PE8  | 136       | SPI1 MOSI      |
| PE9  | 137       | SPI1 CLK       |
| PE10 | 138       | SPI1 MISO      |
| PD0  | 96        | I2C0 SDA       |
| PD12 | 108       | I2C0 SCK       |
