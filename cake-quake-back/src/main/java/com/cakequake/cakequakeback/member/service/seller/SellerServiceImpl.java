package com.cakequake.cakequakeback.member.service.seller;

import com.cakequake.cakequakeback.common.exception.BusinessException;
import com.cakequake.cakequakeback.common.exception.ErrorCode;
import com.cakequake.cakequakeback.common.utils.CustomImageUtils;
import com.cakequake.cakequakeback.member.dto.ApiResponseDTO;
import com.cakequake.cakequakeback.member.dto.seller.SellerModifyDTO;
import com.cakequake.cakequakeback.member.dto.seller.SellerResponseDTO;
import com.cakequake.cakequakeback.member.dto.seller.SellerSignupStep1RequestDTO;
import com.cakequake.cakequakeback.member.dto.seller.SellerSignupStep2RequestDTO;
import com.cakequake.cakequakeback.member.entities.*;
import com.cakequake.cakequakeback.member.repo.MemberRepository;
import com.cakequake.cakequakeback.member.repo.PendingSellerRequestRepository;
import com.cakequake.cakequakeback.member.validator.MemberValidator;
import com.cakequake.cakequakeback.security.service.AuthenticatedUserService;
import com.cakequake.cakequakeback.shop.dto.ShopPreviewDTO;
import com.cakequake.cakequakeback.shop.entities.Shop;
import com.cakequake.cakequakeback.shop.entities.ShopStatus;
import com.cakequake.cakequakeback.shop.repo.ShopRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.Optional;

@Service
@Transactional
@Slf4j
public class SellerServiceImpl implements SellerService{

    private final PendingSellerRequestRepository pendingSellerRequestRepository;
    private final MemberRepository memberRepository;
    private final ShopRepository shopRepository;
    private final PasswordEncoder passwordEncoder;
    private final MemberValidator memberValidator;
    private final CustomImageUtils customImageUtils;

    private final AuthenticatedUserService authenticatedUserService;

    public SellerServiceImpl(PendingSellerRequestRepository pendingSellerRequestRepository, PasswordEncoder passwordEncoder, MemberValidator memberValidator, CustomImageUtils customImageUtils, MemberRepository memberRepository, ShopRepository shopRepository, AuthenticatedUserService authenticatedUserService) {
        this.pendingSellerRequestRepository = pendingSellerRequestRepository;
        this.passwordEncoder = passwordEncoder;
        this.memberValidator = memberValidator;
        this.customImageUtils = customImageUtils;
        this.memberRepository = memberRepository;
        this.shopRepository = shopRepository;
        this.authenticatedUserService = authenticatedUserService;
    }

    @Value("${file.upload.pending-seller-dir}")
    private String pendingSellerDir;


    @Override
    public ApiResponseDTO registerStepOne(SellerSignupStep1RequestDTO requestDTO) {

        SocialType joinType = SocialType.from(requestDTO.getJoinType());
        MultipartFile file = requestDTO.getBusinessCertificate();

        /*
            유효성 형식 검사 - userId, 비밀번호, uname 길이, 전화번호 형식, 가입 방식, 사업자 등록 번호, 대표자 성명, 개업 일자, 매장명
            중복 검사 - userId, 전화번호
        */
        memberValidator.validateSellerSignup(requestDTO);

        String phoneNumber = requestDTO.getPhoneNumber();

        /*
            승인 대기 테이블에서 휴대폰 중복 검사.
            회원 가입 1단계만 진행하고 멈췄을 때: 폰 번호가 이미 있고, 2단계 정보가 null이면 폰 번호 중복 검사 안 함.
         */
        Long tempSellerId = checkPendingSellerProgress(phoneNumber);

        // basic 가입일 때만 비밀번호 인코딩
        String encodedPassword = null;
        if (joinType == SocialType.BASIC) {
            encodedPassword = passwordEncoder.encode(requestDTO.getPassword());
        }

        /* 휴대폰 인증, 사업자 등록 진위여부 검증은 프론트에서 따로 호출 */

        // 파일 처리 - 사업자 등록증 파일
        String savedName = customImageUtils.saveImageFile(file, pendingSellerDir);

        PendingSellerRequest pendingSeller;

        if (tempSellerId == null) {
            // 신규 생성
            pendingSeller = PendingSellerRequest.builder()
                    .userId(requestDTO.getUserId())
                    .uname(requestDTO.getUname())
                    .password(encodedPassword)
                    .phoneNumber(requestDTO.getPhoneNumber())
                    .businessNumber(requestDTO.getBusinessNumber())
                    .bossName(requestDTO.getBossName())
                    .openingDate(requestDTO.getOpeningDate())
                    .shopName(requestDTO.getShopName())
                    .publicInfo(true)
                    .socialType(joinType)
                    .businessCertificateUrl(savedName)
                    .status(SellerRequestStatus.PENDING)
                    .build();

        } else {
            // 1단계 데이터 남아있을 때 업데이트
            pendingSeller = pendingSellerRequestRepository.findById(tempSellerId)
                    .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND_TEMP_SELLER_ID));

            pendingSeller.changeUserId(requestDTO.getUserId());
            pendingSeller.changeUname(requestDTO.getUname());
            pendingSeller.changePassword(encodedPassword);
            pendingSeller.changeBusinessNumber(requestDTO.getBusinessNumber());
            pendingSeller.changeBossName(requestDTO.getBossName());
            pendingSeller.changeOpeningDate(requestDTO.getOpeningDate());
            pendingSeller.changeShopName(requestDTO.getShopName());
            pendingSeller.changeBusinessCertificateUrl(savedName);
        }

        pendingSellerRequestRepository.save(pendingSeller);

        return ApiResponseDTO.builder()
                .success(true)
                .message("1단계 저장 성공하였습니다. 다음 매장 정보 입력 단계로 진행해 주세요.")
                .data(pendingSeller.getTempSellerId())
                .build();
    }

    @Override
    public ApiResponseDTO registerStepTwo(SellerSignupStep2RequestDTO dto) {

        // 가입 2단계 DTO 형식 검사 + 매장 번호 중복 검사
        memberValidator.validateSellerSignup2(dto);
        log.debug("---registerStepTwo---memberValidator 통과---");

        // 1단계에서 저장된 임시 판매자 조회
        PendingSellerRequest pendingSeller = pendingSellerRequestRepository.findById(dto.getTempSellerId())
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND_TEMP_SELLER_ID));

        // 이미지 저장 처리
        String shopImageName = null; // 대표 이미지
        String sanitationImageName = null; // 위생 인증서

        if (dto.getShopImage() != null && !dto.getShopImage().isEmpty()) {
            shopImageName = customImageUtils.saveImageFile(dto.getShopImage(), pendingSellerDir);
        }

        if (dto.getSanitationCertificate() != null && !dto.getSanitationCertificate().isEmpty()) {
            sanitationImageName = customImageUtils.saveImageFile(dto.getSanitationCertificate(), pendingSellerDir);
        }

    /* aws 배포용
        if (dto.getShopImage() != null && !dto.getShopImage().isEmpty()) {
            shopImageName = fileStorageService.storeFile(dto.getShopImage(), "images/shopImages/");
        }

        if (dto.getSanitationCertificate() != null && !dto.getSanitationCertificate().isEmpty()) {
            sanitationImageName = fileStorageService.storeFile(dto.getSanitationCertificate(), "images/sellerCertificates/");
        }
    */

        // 판매자 정보 업데이트
        pendingSeller.changeAddress(dto.getShopAddress());
        pendingSeller.changeShopPhoneNumber(dto.getShopPhoneNumber());
        pendingSeller.changeOpenTime(dto.getOpenTime());
        pendingSeller.changeCloseTime(dto.getCloseTime());
        pendingSeller.changeMainProductDescription(dto.getMainProductDescription());
        pendingSeller.changeShopImageUrl(shopImageName);
        pendingSeller.changeSanitationCertificateUrl(sanitationImageName);

        pendingSellerRequestRepository.save(pendingSeller);

        return ApiResponseDTO.builder()
                .success(true)
                .message("판매자 승인 요청이 접수되었습니다. 관리자의 승인을 기다려주세요.")
                .build();
    }

    @Override
    public ApiResponseDTO getSellerProfile(Long uid) {

        if(uid == null) throw new BusinessException(ErrorCode.NOT_FOUND_UID);

        String currentUserId = authenticatedUserService.getCurrentMember().getUserId();

        SellerResponseDTO sellerDTO = memberRepository.sellerGetOne(uid)
                .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));

        if (!sellerDTO.getRole().equals(MemberRole.SELLER)) {
            throw new BusinessException(ErrorCode.NOT_AUTHORIZED_OTHER);
        }

        if (!sellerDTO.getUserId().equals(currentUserId)) {
            throw new BusinessException(ErrorCode.NOT_AUTHORIZED_OTHER_SELLER);
        }

        ShopPreviewDTO shopPreview = shopRepository.findPreviewByUid(uid)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND_SHOP_ID));

        SellerResponseDTO responseDTO = SellerResponseDTO.builder()
                .uid(sellerDTO.getUid())
                .userId(sellerDTO.getUserId())
                .uname(sellerDTO.getUname())
                .phoneNumber(sellerDTO.getPhoneNumber())
                .shopPreview(shopPreview)
                .build();

        return ApiResponseDTO.builder()
                .success(true)
                .message("판매자 프로필 조회 성공")
                .data(responseDTO)
                .build();
    }

    @Override
    public ApiResponseDTO modifySellerProfile(Long uid, SellerModifyDTO modifyDTO) {
        // url로 넘어온 uid
        if(uid == null) throw new BusinessException(ErrorCode.NOT_FOUND_UID);

        String currentUserId = authenticatedUserService.getCurrentMember().getUserId();

        Member seller = memberRepository.findById(uid)
                .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));

        // 로그인한 유저ID와 조회한 userId가 다를 때
        if (!seller.getUserId().equals(currentUserId)) {
            throw new BusinessException(ErrorCode.NOT_AUTHORIZED_OTHER_SELLER);
        }

        // 전화번호가 기존과 다를 때만 전화번호 형식 + 중복 검사
        if (!seller.getPhoneNumber().equals(modifyDTO.getPhoneNumber())) {
            memberValidator.validatePhoneNumber(modifyDTO.getPhoneNumber());
        }

        /* 휴대폰 인증은 프론트에서 따로 호출 */

        seller.changeUname(modifyDTO.getUname());
        seller.changePhoneNumber(modifyDTO.getPhoneNumber());

        memberRepository.save(seller);

        return ApiResponseDTO.builder()
                .success(true)
                .message("판매자 프로필 수정 성공")
                .build();
    }

    // 판매자 탈퇴 시 상태 변경과 매장 상태도 함께 변경
    @Override
    public ApiResponseDTO withdrawSeller() {

        Member member = authenticatedUserService.getCurrentMember();

        member.withdraw(); // Repository에서 탈퇴 status 세팅(status ACTIVE -> WITHDRAWN)
        memberRepository.save(member);

        if (member.getRole() == MemberRole.SELLER) {
            Shop shop = shopRepository.findActiveShopByUid(member.getUid())
                    .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND_SHOP_ID));

            shop.changeStatus(ShopStatus.CLOSED);
            shopRepository.save(shop); // 상태 저장
        } // end if

        return ApiResponseDTO.builder()
                .success(true)
                .message("탈퇴와 매장 삭제가 완료되었습니다.")
                .build();
    }


    private Long checkPendingSellerProgress(String phoneNumber) {

        Optional<PendingSellerRequest> opt =
                pendingSellerRequestRepository.findByPhoneNumber(phoneNumber);

        if (opt.isEmpty()) return null; // 신규 진행

        PendingSellerRequest temp = opt.get();

        // Step2 완료인지 확인
        boolean step2Complete =
                temp.getAddress() != null && temp.getMainProductDescription() != null;

        // 폰 번호도 db에 있고 2단계 정보도 있는 경우 중복.
        if (step2Complete) {
            throw new BusinessException(ErrorCode.ALREADY_EXIST_PHONE);
        }

        LocalDateTime expiresAt = temp.getRegDate().plusHours(6);

        // 1단계 완료 후 6시간 지나고 2단계 정보도 없으면 db랑 파일 삭제
        if (expiresAt.isBefore(LocalDateTime.now())) {
            customImageUtils.deleteImageFile(temp.getBusinessCertificateUrl(), pendingSellerDir);
            pendingSellerRequestRepository.delete(temp);
            return null;
        }

        customImageUtils.deleteImageFile(temp.getBusinessCertificateUrl(), pendingSellerDir);
        return temp.getTempSellerId();
    }



}
