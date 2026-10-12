package scribe.client.io;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.CRC32;
import java.util.zip.Deflater;
import java.util.zip.DeflaterOutputStream;

public final class PictureFiles {
	public static final double POINTS_PER_PIXEL = 2.5;

	private PictureFiles() {
	}

	public static byte[] png(int width, int height, int[] argb) throws IOException {
		ByteArrayOutputStream raw = new ByteArrayOutputStream(height * (width * 4 + 1));
		for (int y = 0; y < height; y++) {
			raw.write(0);
			for (int x = 0; x < width; x++) {
				int p = argb[y * width + x];
				raw.write(p >> 16 & 255);
				raw.write(p >> 8 & 255);
				raw.write(p & 255);
				raw.write(p >>> 24);
			}
		}
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		out.write(new byte[] {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n'});
		ByteArrayOutputStream header = new ByteArrayOutputStream();
		writeInt(header, width);
		writeInt(header, height);
		header.write(new byte[] {8, 6, 0, 0, 0});
		chunk(out, "IHDR", header.toByteArray());
		chunk(out, "IDAT", deflate(raw.toByteArray()));
		chunk(out, "IEND", new byte[0]);
		return out.toByteArray();
	}

	private static void chunk(OutputStream out, String type, byte[] data) throws IOException {
		byte[] name = type.getBytes(StandardCharsets.US_ASCII);
		writeInt(out, data.length);
		out.write(name);
		out.write(data);
		CRC32 crc = new CRC32();
		crc.update(name);
		crc.update(data);
		writeInt(out, (int) crc.getValue());
	}

	private static void writeInt(OutputStream out, int value) throws IOException {
		out.write(value >>> 24);
		out.write(value >> 16 & 255);
		out.write(value >> 8 & 255);
		out.write(value & 255);
	}

	private static byte[] deflate(byte[] data) throws IOException {
		ByteArrayOutputStream out = new ByteArrayOutputStream(data.length / 4);
		Deflater deflater = new Deflater(6);
		try (DeflaterOutputStream z = new DeflaterOutputStream(out, deflater)) {
			z.write(data);
		} finally {
			deflater.end();
		}
		return out.toByteArray();
	}

	public record Picture(int width, int height, int[] argb, double pointsWide, double pointsHigh) {
	}

	public static byte[] pdf(List<Picture> pages, String title, String author) throws IOException {
		Writer w = new Writer();
		int count = pages.size();
		int first = 4;
		w.object(1, "<< /Type /Catalog /Pages 2 0 R >>");
		StringBuilder kids = new StringBuilder();
		for (int i = 0; i < count; i++) {
			kids.append(first + i * 3).append(" 0 R ");
		}
		w.object(2, "<< /Type /Pages /Kids [ " + kids + "] /Count " + count + " >>");
		w.object(3, "<< /Title " + text(title) + " /Author " + text(author) + " /Creator (Scribe) /Producer (Scribe) >>");
		for (int i = 0; i < count; i++) {
			Picture page = pages.get(i);
			int pageId = first + i * 3;
			String w0 = number(page.pointsWide());
			String h0 = number(page.pointsHigh());
			w.object(pageId, "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 " + w0 + " " + h0 + "] /Resources << /XObject << /Im"
				+ i + " " + (pageId + 2) + " 0 R >> >> /Contents " + (pageId + 1) + " 0 R >>");
			byte[] contents = ("q " + w0 + " 0 0 " + h0 + " 0 0 cm /Im" + i + " Do Q").getBytes(StandardCharsets.US_ASCII);
			w.stream(pageId + 1, "<< /Length " + contents.length + " >>", contents);
			byte[] rgb = deflate(rgbOnWhite(page));
			w.stream(pageId + 2, "<< /Type /XObject /Subtype /Image /Width " + page.width() + " /Height " + page.height()
				+ " /ColorSpace /DeviceRGB /BitsPerComponent 8 /Filter /FlateDecode /Length " + rgb.length + " >>", rgb);
		}
		return w.finish(first + count * 3, 1, 3);
	}

	private static byte[] rgbOnWhite(Picture page) {
		int[] argb = page.argb();
		byte[] out = new byte[argb.length * 3];
		for (int i = 0; i < argb.length; i++) {
			int p = PageRaster.over(0xFFFFFFFF, argb[i]);
			out[i * 3] = (byte) (p >> 16);
			out[i * 3 + 1] = (byte) (p >> 8);
			out[i * 3 + 2] = (byte) p;
		}
		return out;
	}

	static String text(String value) {
		boolean ascii = value.chars().allMatch(c -> c >= 32 && c < 127);
		if (ascii) {
			return "(" + value.replace("\\", "\\\\").replace("(", "\\(").replace(")", "\\)") + ")";
		}
		StringBuilder hex = new StringBuilder("<FEFF");
		for (byte b : value.getBytes(StandardCharsets.UTF_16BE)) {
			hex.append(String.format("%02X", b & 255));
		}
		return hex.append('>').toString();
	}

	private static String number(double value) {
		return value == Math.rint(value) ? Long.toString((long) value) : String.format(java.util.Locale.ROOT, "%.2f", value);
	}

	private static final class Writer {
		private final ByteArrayOutputStream out = new ByteArrayOutputStream();
		private final List<long[]> offsets = new ArrayList<>();

		Writer() throws IOException {
			this.out.write("%PDF-1.4\n%âãÏÓ\n".getBytes(StandardCharsets.ISO_8859_1));
		}

		void object(int id, String body) throws IOException {
			this.offsets.add(new long[] {id, this.out.size()});
			this.out.write((id + " 0 obj\n" + body + "\nendobj\n").getBytes(StandardCharsets.ISO_8859_1));
		}

		void stream(int id, String dictionary, byte[] data) throws IOException {
			this.offsets.add(new long[] {id, this.out.size()});
			this.out.write((id + " 0 obj\n" + dictionary + "\nstream\n").getBytes(StandardCharsets.ISO_8859_1));
			this.out.write(data);
			this.out.write("\nendstream\nendobj\n".getBytes(StandardCharsets.ISO_8859_1));
		}

		byte[] finish(int size, int root, int info) throws IOException {
			long[] byId = new long[size];
			for (long[] o : this.offsets) {
				byId[(int) o[0]] = o[1];
			}
			long xref = this.out.size();
			StringBuilder table = new StringBuilder("xref\n0 " + size + "\n0000000000 65535 f \n");
			for (int id = 1; id < size; id++) {
				table.append(String.format(java.util.Locale.ROOT, "%010d 00000 n \n", byId[id]));
			}
			table.append("trailer\n<< /Size ").append(size).append(" /Root ").append(root).append(" 0 R /Info ").append(info).append(" 0 R >>\nstartxref\n")
				.append(xref).append("\n%%EOF\n");
			this.out.write(table.toString().getBytes(StandardCharsets.ISO_8859_1));
			return this.out.toByteArray();
		}
	}
}
