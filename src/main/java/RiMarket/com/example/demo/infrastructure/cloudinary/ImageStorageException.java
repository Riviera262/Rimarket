package RiMarket.com.example.demo.infrastructure.cloudinary;

public class ImageStorageException extends RuntimeException {
    public ImageStorageException(
            String message,
            Throwable cause
    ){
        super(message, cause);
    }
}
