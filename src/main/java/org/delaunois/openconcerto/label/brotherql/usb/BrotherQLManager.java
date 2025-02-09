/* 
 * USB Driver for printing with Brother QL printers.
 * 
 * Copyright (C) 2024 Cédric de Launois
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package org.delaunois.openconcerto.label.brotherql.usb;

import org.usb4java.BufferUtils;
import org.usb4java.Context;
import org.usb4java.Device;
import org.usb4java.DeviceDescriptor;
import org.usb4java.DeviceHandle;
import org.usb4java.DeviceList;
import org.usb4java.LibUsb;
import org.usb4java.LibUsbException;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.IntBuffer;
import java.util.List;
import java.util.function.BiFunction;
import java.util.logging.Level;
import java.util.logging.Logger;

import static org.delaunois.openconcerto.label.brotherql.usb.BrotherQLPhaseType.PHASE_PRINTING;
import static org.delaunois.openconcerto.label.brotherql.usb.BrotherQLPhaseType.WAITING_TO_RECEIVE;

/**
 * Main class managing communications with the Brother QL printer through USB.
 *
 * @author Cedric de Launois
 */
public class BrotherQLManager {

    private static final Logger LOGGER = Logger.getLogger(BrotherQLManager.class.getName());

    private static final char[] HEX_ARRAY = "0123456789ABCDEF".toCharArray();
    private static final int PRINT_TIMEOUT_MS = 2000;
    private static final int STATUS_SIZE = 32;
    
    private static final byte[] CMD_RESET = new byte[350];
    private static final byte[] CMD_INITIALIZE = new byte[]{0x1B, 0x40};
    private static final byte[] CMD_STATUS_REQUEST = new byte[]{0x1B, 0x69, 0x53};
    private static final byte[] CMD_PRINT_INFORMATION = new byte[]{0x1B, 0x69, 0x7A};
    private static final byte[] CMD_SET_AUTOCUT_ON = new byte[]{0x1B, 0x69, 0x4D, 0x40};
    private static final byte[] CMD_SET_AUTOCUT_OFF = new byte[]{0x1B, 0x69, 0x4D, 0};
    private static final byte[] CMD_SET_CUT_PAGENUMBER = new byte[]{0x1B, 0x69, 0x41};
    private static final byte[] CMD_SET_MARGIN = new byte[]{0x1B, 0x69, 0x64};
    private static final byte[] CMD_RASTER_GRAPHIC_TRANSFER = new byte[]{0x67, 0x00};
    private static final byte[] CMD_PRINT = new byte[]{0x0C};
    private static final byte[] CMD_PRINT_LAST = new byte[]{0x1A};
    private static final byte[] CMD_SWITCH_TO_RASTER = new byte[]{0x1B, 0x69, 0x61, 0x01};

    private static final byte PI_KIND = (byte) 0x02; // Paper type
    private static final byte PI_WIDTH = (byte) 0x04; // Paper width
    private static final byte PI_LENGTH = (byte) 0x08; // Paper length
    private static final byte PI_QUALITY = (byte) 0x40; // Give priority to print quality
    private static final byte PI_RECOVER = (byte) 0x80; // Always ON
    private static final byte STARTING_PAGE = 0;

    /**
     * The vendor ID of the Brother QL Printer.
     */
    private static final short BROTHER_VENDOR_ID = 0x04f9;

    /**
     * The USB interface number of the Brother QL Printer.
     */
    private static final byte INTERFACE = 0;

    /**
     * The USB input endpoint of the Brother QL Printer.
     */
    private static final byte IN_ENDPOINT = (byte) 0x81;

    /**
     * The USB output endpoint of the Brother QL Printer.
     */
    private static final byte OUT_ENDPOINT = 0x02;

    /**
     * The communication timeout in milliseconds.
     */
    private static final int TIMEOUT = 5000;

    private Context context;
    private DeviceHandle handle;
    private DeviceDescriptor deviceDescriptor;
    private BrotherQLPrinterId printerId;

    public BrotherQLManager() {
        handle = null;
        context = new Context();
        int result = LibUsb.init(context);
        if (result != LibUsb.SUCCESS)
            throw new LibUsbException(Rx.msg("libusb.initerror"), result);
        LibUsb.setOption(context, LibUsb.OPTION_LOG_LEVEL, LibUsb.LOG_LEVEL_INFO);
    }

    /**
     * Opens the Brother printer device.
     * 
     * @throws IllegalStateException if the printer is already opened
     * @throws LibUsbException if the USB connection could not be established
     */
    public void open() throws LibUsbException, IllegalStateException {
        if (handle != null) {
            throw new IllegalStateException(Rx.msg("libusb.alreadyopened"));
        }

        Device device = findDevice();
        if (device == null) {
            throw new IllegalStateException(Rx.msg("libusb.nodevicelist"));
        }

        handle = new DeviceHandle();
        int result;
        result = LibUsb.open(device, handle);
        switch (result) {
            case LibUsb.SUCCESS:
                break;
            case LibUsb.ERROR_NO_MEM:
                throw new IllegalStateException(Rx.msg("libusb.nomem"));
            case LibUsb.ERROR_ACCESS:
                throw new IllegalStateException(Rx.msg("libusb.noaccess"));
            case LibUsb.ERROR_NO_DEVICE:
                throw new IllegalStateException(Rx.msg("libusb.nodevice"));
            default:
                throw new LibUsbException(Rx.msg("libusb.unknown"), result);
        }

        result = LibUsb.setAutoDetachKernelDriver(handle, true);
        if (result != LibUsb.SUCCESS) {
            LOGGER.log(Level.WARNING, "setAutoDetachKernelDriver failed: " + result);
        }

        LibUsb.setConfiguration(handle, 1);
        IntBuffer intBuffer = IntBuffer.allocate(1);
        result = LibUsb.getConfiguration(handle, intBuffer);
        switch (result) {
            case LibUsb.SUCCESS:
                break;
            case LibUsb.ERROR_NO_DEVICE:
                LibUsb.close(handle);
                throw new LibUsbException(Rx.msg("libusb.nodevice"), result);
            default:
                LibUsb.close(handle);
                throw new LibUsbException(Rx.msg("libusb.noconfig"), result);
        }

        result = LibUsb.claimInterface(handle, INTERFACE);
        switch (result) {
            case LibUsb.SUCCESS:
                break;
            case LibUsb.ERROR_NOT_FOUND:
                LibUsb.close(handle);
                throw new LibUsbException(Rx.msg("libusb.notfound"), result);
            case LibUsb.ERROR_BUSY:
                LibUsb.close(handle);
                throw new LibUsbException(Rx.msg("libusb.busy"), result);
            case LibUsb.ERROR_NO_DEVICE:
                LibUsb.close(handle);
                throw new LibUsbException(Rx.msg("libusb.nodevice"), result);
            default:
                LibUsb.close(handle);
                throw new LibUsbException(Rx.msg("libusb.noclaim"), result);
        }

        reset();
    }

    /**
     * Reset the printer state.
     */
    public void reset() {
        write(handle, CMD_RESET);
        write(handle, CMD_INITIALIZE);
    }

    /**
     * Gets the printer identification.
     * The device must be opened first.
     * 
     * @return the printer id or null if no Brother printer were detected
     */
    public BrotherQLPrinterId getPrinterId() {
        if (deviceDescriptor == null) {
            return null;
        }
        return printerId;
    }

    /**
     * Sends to the printer a request for status and reads back the status.
     * Must not be called while printing.
     * The device must be opened first.
     * 
     * @return the status
     */
    public BrotherQLStatus requestDeviceStatus() {
        if (handle == null) {
            return new BrotherQLStatus(null, printerId, Rx.msg("libusb.notopened"));
        }

        write(handle, CMD_STATUS_REQUEST);
        return readDeviceStatus();
    }

    /**
     * Reads the status of the printer.
     * The device must be opened first.
     * 
     * @return the status or null if no status were received
     */
    public BrotherQLStatus readDeviceStatus() {
        if (handle == null) {
            return new BrotherQLStatus(null, printerId, Rx.msg("libusb.notopened"));
        }

        BrotherQLStatus brotherQLStatus;
        ByteBuffer response = readStatus(handle);
        
        if (response == null) {
            brotherQLStatus = new BrotherQLStatus(null, printerId);
            
        } else {
            byte[] status = new byte[32];
            response.get(status);
            brotherQLStatus = new BrotherQLStatus(status, printerId);
        }
        
        LOGGER.log(Level.FINE, "Status is " + brotherQLStatus);
        return brotherQLStatus;
    }

    public BrotherQLMedia getMediaDefinition(BrotherQLStatus status) {
        return BrotherQLMedia.identify(status);
    }

    /**
     * Print the given Job.
     * 
     * @param job the job to print
     * @param statusListener a lambda called after each print. The lambda receives as argument the page 
     *                       currently printed (starting at 0) and the current status, 
     *                       and must return a boolean telling wheter the print must continue.
     * @throws IOException if a print error occurred
     */
    public void printJob(BrotherQLJob job, BiFunction<Integer, BrotherQLStatus, Boolean> statusListener) throws IOException {
        if (job == null
                || job.getImages() == null
                || job.getImages().isEmpty()
                || job.getMedia() == null
                || job.getPrinterId() == null) {
            return;
        }

        checkJob(job);
        sendControlCode(job);
        List<BufferedImage> images = job.getImages();

        for (int i = 0; i < images.size(); i++) {
            sendPrintData(images.get(i), job.getMedia());

            boolean last = i == images.size() - 1;
            byte[] pc = last ? CMD_PRINT_LAST : CMD_PRINT;
            log(pc);

            write(handle, pc);

            BrotherQLStatus status = readDeviceStatus();
            if (statusListener != null) {
                // Should read PHASE_CHANGE PHASE_PRINTING
                if (!statusListener.apply(i, status)) {
                    break;
                }
            }

            int timeleft = PRINT_TIMEOUT_MS;
            while (timeleft > 0 && (status == null || PHASE_PRINTING.equals(status.getPhaseType()))) {
                timeleft -= 200;
                sleep(200);
                status = readDeviceStatus();    
            }
            
            if (statusListener != null) {
                if (!statusListener.apply(i, status)) {
                    break;
                }
            }
            
            if (shouldStopPrint(status)) {
                break;
            }
            
            sleep(job.getDelay());
        }
    }
    
    private boolean shouldStopPrint(BrotherQLStatus status) {
        if (status == null) {
            LOGGER.log(Level.WARNING, "Could not get printer status within " + PRINT_TIMEOUT_MS + " ms. Stop printing.");
            return true;
        }

        if (PHASE_PRINTING.equals(status.getPhaseType())) {
            LOGGER.log(Level.WARNING, "Printer did not finish printing within " + PRINT_TIMEOUT_MS + " ms. Stop printing.");
            return true;
        }

        if (BrotherQLStatusType.ERROR_OCCURRED.equals(status.getStatusType())) {
            LOGGER.log(Level.WARNING, "Printer is in error. Stop printing.");
            return true;
        }
        
        if (!WAITING_TO_RECEIVE.equals(status.getPhaseType())) {
            LOGGER.log(Level.WARNING, "Printer is not ready to continue. Stop printing.");
            return true;
        }
        
        return false;
    }

    private void checkJob(BrotherQLJob job) {
        BrotherQLMedia media = job.getMedia();

        BufferedImage img = job.getImages().get(0);
        int bodyLengthPx = img.getHeight();
        int bodyWidthPx = img.getWidth();
        LOGGER.log(Level.FINE, "Image size: " + bodyWidthPx + " x " + bodyLengthPx);
        LOGGER.log(Level.FINE, "Expected Image size: " + media.bodyWidthPx + " x " + media.bodyLengthPx);

        if (bodyWidthPx != media.bodyWidthPx) {
            throw new IllegalArgumentException("Image Width (" + bodyWidthPx + "px) is expected to be " + media.bodyWidthPx + "px");
        }

        if (BrotherQLMediaType.CONTINUOUS_LENGTH_TAPE.equals(media.mediaType)) {
            if (bodyLengthPx < printerId.clMinLengthPx) {
                throw new IllegalArgumentException("Image Height (" + bodyLengthPx + "px) must be greater than or equal to " + printerId.clMinLengthPx);
            }
            if (bodyLengthPx > printerId.clMaxLengthPx) {
                throw new IllegalArgumentException("Image Height (" + bodyLengthPx + "px) must be lower than or equal to " + printerId.clMaxLengthPx);
            }
        } else {
            if (bodyLengthPx != media.bodyLengthPx) {
                throw new IllegalArgumentException("Expected image Height (" + bodyLengthPx + ") is expected to be " + media.bodyLengthPx + "px");
            }
        }
    }
    
    private void sendControlCode(BrotherQLJob job) throws IOException {
        BrotherQLMedia media = job.getMedia();
        ByteArrayOutputStream bos = new ByteArrayOutputStream();

        // Switch to raster if multiple modes exist
        if (!printerId.rasterOnly) {
            bos.write(CMD_SWITCH_TO_RASTER);
        }

        // Add print info
        bos.write(CMD_PRINT_INFORMATION);
        byte pi = (byte) (PI_KIND | PI_WIDTH | PI_QUALITY | PI_RECOVER);
        if (!BrotherQLMediaType.CONTINUOUS_LENGTH_TAPE.equals(media.mediaType)) {
            pi |= PI_LENGTH;
        }
        bos.write(pi);

        int bodyLengthPx = job.getImages().get(0).getHeight();
        bos.write(media.mediaType.code);
        bos.write(media.labelWidthMm & 0xFF);
        bos.write(media.labelLengthMm & 0xFF);
        bos.write(bodyLengthPx & 0xFF);
        bos.write((bodyLengthPx >> 8) & 0xFF);
        bos.write((bodyLengthPx >> 16) & 0xFF);
        bos.write((bodyLengthPx >> 24) & 0xFF);
        bos.write(STARTING_PAGE);
        bos.write(0);

        // Add autocut
        if (job.isAutocut()) {
            bos.write(CMD_SET_AUTOCUT_ON);
            bos.write(CMD_SET_CUT_PAGENUMBER);
            bos.write(job.getCutEach() & 0xFF);
        } else {
            bos.write(CMD_SET_AUTOCUT_OFF);
        }

        // Set margins (in dots)
        bos.write(CMD_SET_MARGIN);
        bos.write(job.getFeedAmount() & 0xFF);
        bos.write((job.getFeedAmount() >> 8) & 0xFF);

        byte[] bytes = bos.toByteArray();
        log(bytes);
        write(handle, bytes);
    }
    
    private void sendPrintData(BufferedImage img, BrotherQLMedia media) throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        BitOutputStream bitOutputStream = new BitOutputStream(bos);

        for (int y = 0; y < img.getHeight(); y++) {
            bos.reset();
            bos.write(CMD_RASTER_GRAPHIC_TRANSFER);
            bos.write(media.rgtSizeBytes);

            // Write left margin
            for (int i = 0; i < media.leftMarginPx; i++) {
                bitOutputStream.write(0);
            }

            // Write body
            for (int x = media.bodyWidthPx - 1; x >= 0; x--) {
                bitOutputStream.write(((byte) img.getRGB(x, y) & 0xFF) == 0 ? 1 : 0);
            }

            // Write right margin
            for (int i = 0; i < media.leftMarginPx; i++) {
                bitOutputStream.write(0);
            }

            bitOutputStream.close();
            byte[] bitRaster = bos.toByteArray();
            log(bitRaster);
            write(handle, bitRaster);
        }
    }

    public void close() {
        if (handle != null && deviceDescriptor != null) {
            LibUsb.close(handle);
            handle = null;
        }
        LibUsb.exit(context);
        context = new Context();
    }

    private Device findDevice() {
        // Read the USB device list
        DeviceList list = new DeviceList();
        int result = LibUsb.getDeviceList(context, list);
        if (result < 0) {
            throw new LibUsbException(Rx.msg("libusb.nodevicelist"), result);
        }

        try {
            // Iterate over all devices and scan for the right one
            for (Device device : list) {
                deviceDescriptor = new DeviceDescriptor();
                result = LibUsb.getDeviceDescriptor(device, deviceDescriptor);
                if (result != LibUsb.SUCCESS) {
                    throw new LibUsbException(Rx.msg("libusb.devicereadfailure"), result);
                }
                printerId = BrotherQLPrinterId.fromCode(deviceDescriptor.idProduct());
                if (deviceDescriptor.idVendor() == BROTHER_VENDOR_ID && printerId != null) {
                    return device;
                }
            }
        } finally {
            // Ensure the allocated device list is freed
            LibUsb.freeDeviceList(list, true);
        }

        // Device not found
        return null;
    }

    /**
     * Writes some data to the device.
     *
     * @param handle The device handle.
     * @param data   The data to send to the device.
     */
    private static void write(DeviceHandle handle, byte[] data) {
        ByteBuffer buffer = BufferUtils.allocateByteBuffer(data.length);
        buffer.put(data);
        write(handle, buffer);
    }

    private static void write(DeviceHandle handle, ByteBuffer buffer) {
        IntBuffer transferred = BufferUtils.allocateIntBuffer();

        int result = LibUsb.bulkTransfer(handle, OUT_ENDPOINT, buffer, transferred, TIMEOUT);

        if (result != LibUsb.SUCCESS) {
            throw new LibUsbException(Rx.msg("libusb.senderror"), result);
        }
    }

    /**
     * Reads status data from the device.
     *
     * @param handle The device handle.
     * @return The read data or null if did not receive size bytes
     */
    private static ByteBuffer readStatus(DeviceHandle handle) {
        ByteBuffer buffer = BufferUtils.allocateByteBuffer(STATUS_SIZE).order(ByteOrder.LITTLE_ENDIAN);

        int read = rawread(handle, buffer);
        if (read == 0) {
            try {
                Thread.sleep(20);
                read = rawread(handle, buffer);
            } catch (InterruptedException e) {
                // Ignore
            }
        }
        
        if (read < 32) {
            LOGGER.log(Level.WARNING, "Incomplete read : " + read + " < " + 32 + " bytes");
            return null;
        }

        return buffer;
    }

    private static int rawread(DeviceHandle handle, ByteBuffer buffer) {
        IntBuffer transferred = BufferUtils.allocateIntBuffer();
        int result = LibUsb.bulkTransfer(handle, IN_ENDPOINT, buffer, transferred, TIMEOUT);
        if (result != LibUsb.SUCCESS) {
            LOGGER.log(Level.WARNING, Rx.msg("libusb.readerror") + result);
        }
        return transferred.get();
    }
    
    private static void log(byte[] bytes) {
        if (LOGGER.isLoggable(Level.FINER)) {
            LOGGER.log(Level.FINER, String.valueOf(toHexChar(bytes)));
        }
    }

    private static char[] toHexChar(byte[] bytes) {
        char[] hexChars = new char[bytes.length * 2];
        for (int i = 0; i < bytes.length; i++) {
            int v = bytes[i] & 0xFF;
            hexChars[i * 2] = HEX_ARRAY[v >>> 4];
            hexChars[i * 2 + 1] = HEX_ARRAY[v & 0x0F];
        }
        return hexChars;
    }
    
    private void sleep(int millis) {
        if (millis <= 0) {
            return;
        }
        
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            // Ignore
        }
    }

}
