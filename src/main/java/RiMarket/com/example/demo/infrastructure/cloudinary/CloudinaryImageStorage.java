    package RiMarket.com.example.demo.infrastructure.cloudinary;
    
    import RiMarket.com.example.demo.module.catalog.port.ImageStorage;
    import RiMarket.com.example.demo.module.catalog.port.StoredImage;
    import com.cloudinary.Cloudinary;
    import com.cloudinary.utils.ObjectUtils;
    import lombok.RequiredArgsConstructor;
    import org.springframework.stereotype.Component;
    
    import java.io.IOException;
    import java.util.Map;
    
    @Component
    @RequiredArgsConstructor
    public class CloudinaryImageStorage implements ImageStorage {
        private final Cloudinary cloudinary;
    
        @Override
        public StoredImage upload(byte[] data, String folder){
            try {
                Map<?, ?> result = cloudinary.uploader().upload(data, ObjectUtils.asMap("asset_folder", folder, "resource_type", "image"));
    
                return new StoredImage(
                    result.get("public_id").toString(),
                    result.get("secure_url").toString()
                );
    
            } catch (IOException exception){
    
                throw new ImageStorageException(
                    "Failed to upload image",
                    exception
                );
            }
        }
    
        @Override
        public void delete(String publicId){
            try{
                Map<?, ?> result = cloudinary.uploader().destroy(publicId,
                        ObjectUtils.asMap("resource_type", "image", "invalidate", true));
                Object outcome = result.get("result");
                if (!"ok".equals(outcome) && !"not found".equals(outcome)) {
                    throw new ImageStorageException("Storage did not confirm image deletion", null);
                }
    
            } catch (IOException exception){
    
                throw new ImageStorageException(
                    "Failed to delete image",
                    exception
                );
            }
        }
    }
