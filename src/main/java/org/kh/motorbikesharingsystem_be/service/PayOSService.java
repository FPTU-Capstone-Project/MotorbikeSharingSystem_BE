package org.kh.motorbikesharingsystem_be.service;

import vn.payos.type.CheckoutResponseData;

public interface PayOSService {
    CheckoutResponseData createPaymentLink(Long orderCode) throws Exception;
}
