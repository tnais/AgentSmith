package org.gianoziaorientale.ingestor;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.LinkOption;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Set;
import org.gianoziaorientale.rag.DocumentFilePolicy;

final class DocumentTextReader {

    byte[] read(Path file) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (SeekableByteChannel channel = java.nio.file.Files.newByteChannel(
                file, Set.<OpenOption>of(StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS))) {
            ByteBuffer buffer = ByteBuffer.allocate(8_192);
            int read;
            while ((read = channel.read(buffer)) != -1) {
                if (read == 0) {
                    continue;
                }
                buffer.flip();
                if (bytes.size() + buffer.remaining() > DocumentFilePolicy.MAX_FILE_BYTES) {
                    throw new IOException("file exceeds the configured maximum size");
                }
                bytes.write(buffer.array(), buffer.position(), buffer.remaining());
                buffer.clear();
            }
        }
        return bytes.toByteArray();
    }
}
