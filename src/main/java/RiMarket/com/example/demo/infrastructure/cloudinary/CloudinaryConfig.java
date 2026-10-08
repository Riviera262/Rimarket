package RiMarket.com.example.demo.infrastructure.cloudinary;

import com.cloudinary.Cloudinary;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.HashMap;
import java.util.Map;

@Configuration
@EnableConfigurationProperties(CloudinaryProperties.class)
public class CloudinaryConfig {
    @Bean
    public Cloudinary cloudinary(CloudinaryProperties properties){
        Map<String, Object> config = new HashMap<>();

        config.put("cloud_name", properties.cloudName());

        config.put("api_key", properties.apiKey());

        config.put("api_secret", properties.apiSecret());

        config.put("secure", true);

        return new Cloudinary(config);
    }
}
