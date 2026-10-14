package com.example.ticketing.asset;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

/** listAssets giữ đủ trường tìm kiếm như searchAssets cũ (name, assetNumber, serialNumber, ipAddress). */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class AssetListSearchTest {

    @Autowired private AssetService assetService;
    @Autowired private AssetRepository assets;

    @Test
    void searchMatchesSerialAndIp() {
        Asset a = new Asset();
        a.setAssetNumber("AST-S01");
        a.setName("Switch tầng 2");
        a.setAssetType(Asset.AssetType.NETWORK_DEVICE);
        a.setSerialNumber("SN-XYZ-778");
        a.setIpAddress("10.20.30.40");
        assets.save(a);
        assertThat(assetService.listAssets(Specification.unrestricted(), null, null, "xyz-778", PageRequest.of(0, 10))
                .getContent()).extracting(Asset::getAssetNumber).containsExactly("AST-S01");
        assertThat(assetService.listAssets(Specification.unrestricted(), null, null, "10.20.30", PageRequest.of(0, 10))
                .getContent()).extracting(Asset::getAssetNumber).containsExactly("AST-S01");
    }
}
