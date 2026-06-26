package com.upc.Service;

import com.upc.DTO.HomeBannerDTO;
import org.springframework.web.multipart.MultipartFile;

public interface HomeBannerService {
    HomeBannerDTO obtenerBanner();
    HomeBannerDTO actualizarBanner(HomeBannerDTO bannerDTO, MultipartFile file);
}