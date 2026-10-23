package com.bcp.wallet.identity;

record AccountRegistration(
        String accountType,
        String documentType,
        String documentNumber,
        String firstNames,
        String paternalSurname,
        String maternalSurname,
        String legalName,
        String tradeName,
        String email,
        String phone,
        String googleSubject) {
}

record RegisteredAccount(
        String id,
        String accountType,
        String documentType,
        String maskedDocument,
        String status,
        String createdAt) {
}

record EncryptedProfile(String nonce, String ciphertext) {
}

record GoogleIdentity(String subject, String email, String firstNames, String surname) {
}