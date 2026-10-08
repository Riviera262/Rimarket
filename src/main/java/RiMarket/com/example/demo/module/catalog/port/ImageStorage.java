package RiMarket.com.example.demo.module.catalog.port;

public interface ImageStorage {
    /** Upload a fresh asset with a new ID; never overwrite an existing asset. */
    StoredImage upload(byte[] data, String folder);

    void delete(String publicId);
}
