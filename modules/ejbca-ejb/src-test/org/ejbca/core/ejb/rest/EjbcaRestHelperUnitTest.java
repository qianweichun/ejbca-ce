/*************************************************************************
 *                                                                       *
 *  EJBCA Community: The OpenSource Certificate Authority                *
 *                                                                       *
 *  This software is free software; you can redistribute it and/or       *
 *  modify it under the terms of the GNU Lesser General Public           *
 *  License as published by the Free Software Foundation; either         *
 *  version 2.1 of the License, or any later version.                    *
 *                                                                       *
 *  See terms of license at gnu.org.                                     *
 *                                                                       *
 *************************************************************************/

package org.ejbca.core.ejb.rest;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.InvalidAlgorithmParameterException;
import java.security.KeyPair;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Collection;

import org.apache.log4j.Logger;
import org.bouncycastle.asn1.ASN1Encodable;
import org.bouncycastle.asn1.ASN1EncodableVector;
import org.bouncycastle.asn1.DERSequence;
import org.bouncycastle.asn1.DERSet;
import org.bouncycastle.asn1.pkcs.PKCSObjectIdentifiers;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.asn1.x509.Extensions;
import org.bouncycastle.asn1.x509.ExtensionsGenerator;
import org.bouncycastle.asn1.x509.GeneralNames;
import org.bouncycastle.operator.OperatorCreationException;
import org.bouncycastle.pkcs.PKCS10CertificationRequest;
import org.cesecore.authentication.tokens.AuthenticationToken;
import org.cesecore.authorization.AuthorizationDeniedException;
import org.cesecore.certificates.ca.CADoesntExistsException;
import org.cesecore.certificates.ca.CAInfo;
import org.cesecore.certificates.ca.CaSessionLocal;
import org.cesecore.certificates.ca.X509CAInfo;
import org.cesecore.certificates.ca.catoken.CAToken;
import org.cesecore.certificates.certificate.certextensions.standard.SubjectDirectoryAttributes;
import org.cesecore.certificates.certificateprofile.CertificateProfileDoesNotExistException;
import org.cesecore.certificates.certificateprofile.CertificateProfileSessionLocal;
import org.cesecore.certificates.endentity.EndEntityInformation;
import org.cesecore.keys.util.KeyTools;
import org.cesecore.util.CertTools;
import org.cesecore.util.CryptoProviderTools;
import org.easymock.EasyMock;
import org.easymock.EasyMockRunner;
import org.easymock.Mock;
import org.easymock.TestSubject;
import org.ejbca.core.EjbcaException;
import org.ejbca.core.ejb.authentication.web.WebAuthenticationProviderSessionLocal;
import org.ejbca.core.ejb.ra.raadmin.EndEntityProfileSessionLocal;
import org.ejbca.core.model.era.RaMasterApiProxyBeanLocal;
import org.ejbca.core.model.ra.raadmin.EndEntityProfile;
import org.ejbca.core.model.ra.raadmin.EndEntityProfileNotFoundException;
import org.ejbca.core.protocol.rest.EnrollPkcs10CertificateRequest;
import org.junit.Test;
import org.junit.runner.RunWith;

import static org.easymock.EasyMock.expect;
import static org.easymock.EasyMock.replay;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;


/**
 * This class contains unit tests for EjbcaRestHelper class
 */
@RunWith(EasyMockRunner.class)
public class EjbcaRestHelperUnitTest {
    private static final Logger log = Logger.getLogger(EjbcaRestHelperUnitTest.class);

    @TestSubject
    EjbcaRestHelperSessionBean testClass = new EjbcaRestHelperSessionBean();

    @Mock
    private WebAuthenticationProviderSessionLocal authenticationSession;

    @Mock
    private RaMasterApiProxyBeanLocal raMasterApiProxyBean;

    @Mock
    private EndEntityProfileSessionLocal endEntityProfileSessionBean;

    @Mock
    private CertificateProfileSessionLocal certificateProfileSessionBean;
    @Mock
    private CaSessionLocal caSessionBean;

    final String csr = "-----BEGIN CERTIFICATE REQUEST-----\n"
            + "MIIDWDCCAkACAQAwYTELMAkGA1UEBhMCRUUxEDAOBgNVBAgTB0FsYWJhbWExEDAO\n"
            + "BgNVBAcTB3RhbGxpbm4xFDASBgNVBAoTC25hYWJyaXZhbHZlMRgwFgYDVQQDEw9o\n"
            + "ZWxsbzEyM3NlcnZlcjYwggEiMA0GCSqGSIb3DQEBAQUAA4IBDwAwggEKAoIBAQDe\n"
            + "lRzGyeXlCQL3lgLjzEn4qcbD0qtth8rXAwjg/eEN1u8lpQp3GtByWm6LeeB7CEyP\n"
            + "fyy+rW9C7nQmXvJ09cJaLAlETpGjjfZLy6pHzle/D192THB2MYZRuvvAPCfpjjnV\n"
            + "hP9sYn7GN7kCaYh61fvlD2fVquzqRdz9kjib3mVEmswkS6lHuAPIsmI7SG9UuvPR\n"
            + "ND1DOsmVwqOL62EOE/RlHRStxZDHQDoYMqZISAO5arpbDujn666IVqLs1QpsQ5Ih\n"
            + "Avxlw+EGNzzYMCbFEkuGs5JK/YNS7JL3JrvMor8XLngaatbteztK0o+khgT2K9x7\n"
            + "BCkqEoz9iJrmO3B8JDATAgMBAAGggbEwga4GCSqGSIb3DQEJDjGBoDCBnTBQBgNV\n"
            + "HREESTBHggtzb21lZG5zLmNvbYcEwKgBB4ISc29tZS5vdGhlci5kbnMuY29tpB4w\n"
            + "HDENMAsGA1UEAxMEVGVzdDELMAkGA1UEBxMCWFgwMQYDVR0lBCowKAYIKwYBBQUH\n"
            + "AwEGCCsGAQUFBwMCBggrBgEFBQcDAwYIKwYBBQUHAwQwCQYDVR0TBAIwADALBgNV\n"
            + "HQ8EBAMCBeAwDQYJKoZIhvcNAQELBQADggEBAM2cW62D4D4vxaKVtIYpgolbD0zv\n"
            + "WyEA6iPa4Gg2MzeLJVswQoZXCj5gDOrttHDld3QQTDyT9GG0Vg8N8Tr9i44vUr7R\n"
            + "gK5w+PMq2ExGS48YrCoMqV+AJHaeXP+gi23ET5F6bIJnpM3ru6bbZC5IUE04YjG6\n"
            + "xQux6UsxQabuaTrHpExMgYjwJsekEVe13epUq5OiEh7xTJaSnsZm+Ja+MV2pn0gF\n"
            + "3V1hMBajTMGN9emWLR6pfj5P7QpVR4hkv3LvgCPf474pWA9l/4WiKBzrI76T5yz1\n"
            + "KoobCZQ2UrqnKFGEbdoNFchb2CDgdLnFu6Tbf6MW5zO5ypOIUih61Zf9Qyo=\n"
            + "-----END CERTIFICATE REQUEST-----\n";


    @Test(expected = CADoesntExistsException.class)
    public void shouldFailOnFindingCertificateAuthorityByName() throws Exception {

        // given
        final String endEntityProfileName = "eep1";
        final String certificateAuthorityName = "CA1";
        final String certificateProfileName = "CP1";

        final X509Certificate mockX509Cert = EasyMock.mock(X509Certificate.class);

        final X509Certificate[] certs = new X509Certificate[1];
        certs[0] = mockX509Cert;

        final AuthenticationToken authenticationToken = EasyMock.mock(AuthenticationToken.class);

        expect(caSessionBean.getCAInfo(authenticationToken, certificateAuthorityName)).andReturn(null);
        replay(caSessionBean);
        final EnrollPkcs10CertificateRequest request = new EnrollPkcs10CertificateRequest.Builder()
                .certificateRequest(csr)
                .certificateProfileName(certificateProfileName)
                .endEntityProfileName(endEntityProfileName)
                .certificateAuthorityName(certificateAuthorityName)
                .build();

        // when
        testClass.convertToEndEntityInformation(authenticationToken, request);

        // then
        EasyMock.verify();
    }

    @Test(expected = CertificateProfileDoesNotExistException.class)
    public void shouldFailOnFindingCertificateProfileByName() throws Exception {
        // given
        String endEntityProfileName = "eep1";
        String certificateAuthorityName = "CA1";
        String certificateProfileName = "CP1";
        int certificateProfileId = 1;
        String subjectDn = CertTools.stringToBCDNString("CN=mydn"); 
        String name = "test123";
        int status = 20;
        String encodedValidity = "";
        int signedby = 1;

        X509Certificate mockX509Cert = EasyMock.mock(X509Certificate.class);

        X509Certificate[] certs = new X509Certificate[1];
        certs[0] = mockX509Cert;

        Collection<Certificate> certificatechain = new ArrayList<>();
        CAToken caToken = EasyMock.mock(CAToken.class);

        CAInfo caInfo = X509CAInfo.getDefaultX509CAInfo(subjectDn, name, status, certificateProfileId, encodedValidity, signedby, certificatechain, caToken);

        AuthenticationToken authenticationToken = EasyMock.mock(AuthenticationToken.class);

        expect(caSessionBean.getCAInfo(authenticationToken, certificateAuthorityName)).andReturn(caInfo);
        expect(certificateProfileSessionBean.getCertificateProfileId(certificateProfileName)).andReturn(0);

        replay(caSessionBean);
        replay(certificateProfileSessionBean);

        EnrollPkcs10CertificateRequest request = new EnrollPkcs10CertificateRequest.Builder()
                .certificateRequest(csr)
                .certificateProfileName(certificateProfileName)
                .endEntityProfileName(endEntityProfileName)
                .certificateAuthorityName(certificateAuthorityName)
                .build();

        // when
        testClass.convertToEndEntityInformation(authenticationToken, request);

        // then
        EasyMock.verify();
    }


    @Test(expected = EndEntityProfileNotFoundException.class)
    public void shouldFailOnFindingEndEntityProfileByName() throws Exception {
        // given
        String endEntityProfileName = "eep1";
        String certificateAuthorityName = "CA1";
        String certificateProfileName = "CP1";
        int certificateProfileId = 1;
        String subjectDn = CertTools.stringToBCDNString("CN=mydn"); 
        String name = "test123";
        int status = 20;
        String encodedValidity = "";
        int signedby = 1;

        X509Certificate mockX509Cert = EasyMock.mock(X509Certificate.class);

        X509Certificate[] certs = new X509Certificate[1];
        certs[0] = mockX509Cert;

        Collection<Certificate> certificatechain = new ArrayList<>();
        CAToken caToken = EasyMock.mock(CAToken.class);

        CAInfo caInfo = X509CAInfo.getDefaultX509CAInfo(subjectDn, name, status, certificateProfileId, encodedValidity, signedby, certificatechain, caToken);

        AuthenticationToken authenticationToken = EasyMock.mock(AuthenticationToken.class);

        expect(caSessionBean.getCAInfo(authenticationToken, certificateAuthorityName)).andReturn(caInfo);
        expect(certificateProfileSessionBean.getCertificateProfileId(certificateProfileName)).andReturn(1);
        expect(endEntityProfileSessionBean.getEndEntityProfileId(endEntityProfileName)).andThrow(new EndEntityProfileNotFoundException());

        replay(caSessionBean);
        replay(certificateProfileSessionBean);
        replay(endEntityProfileSessionBean);

        EnrollPkcs10CertificateRequest request = new EnrollPkcs10CertificateRequest.Builder()
                .certificateRequest(csr)
                .certificateProfileName(certificateProfileName)
                .endEntityProfileName(endEntityProfileName)
                .certificateAuthorityName(certificateAuthorityName)
                .build();

        // when
        testClass.convertToEndEntityInformation(authenticationToken, request);

        // then
        EasyMock.verify();
    }

    @Test
    public void shouldConvertToEndEntityInformationSubjectDirectoryAttributes() throws InvalidAlgorithmParameterException, OperatorCreationException, IOException, AuthorizationDeniedException, EjbcaException, CADoesntExistsException, CertificateProfileDoesNotExistException {
        //given
        String endEntityProfileName = "eep1";
        int endEntityProfileId = 7;
        String certificateAuthorityName = "CA1";
        String certificateProfileName = "CP1";
        String username = "testuser";
        AuthenticationToken authenticationToken = EasyMock.mock(AuthenticationToken.class);
        EndEntityProfile endEntityProfile = EasyMock.createNiceMock(EndEntityProfile.class);

        final String dn = "CN=pkcs10requesttestWithSubDirAtt";
        String dirAttrString = "DATEOFBIRTH=19840202, GENDER=F, COUNTRYOFCITIZENSHIP=EE, COUNTRYOFRESIDENCE=FR";
        String altNameString = "dNSName=foo.example.com, iPAddress=10.0.0.1";
        String csrRequest = generateCsrWithSubjectDirectoryAttributesAndSAN(dn, dirAttrString, altNameString);

        String subjectDn = CertTools.stringToBCDNString("CN=mydn");
        Collection<Certificate> certificatechain = new ArrayList<>();
        CAToken caToken = EasyMock.mock(CAToken.class);
        CAInfo caInfo = X509CAInfo.getDefaultX509CAInfo(subjectDn, "test123", 20, 9,
                "", 1, certificatechain, caToken);

        expect(caSessionBean.getCAInfo(authenticationToken, certificateAuthorityName)).andReturn(caInfo);
        expect(certificateProfileSessionBean.getCertificateProfileId(certificateProfileName)).andReturn(3);
        expect(endEntityProfileSessionBean.getEndEntityProfileId(endEntityProfileName)).andReturn(endEntityProfileId);
        expect(endEntityProfileSessionBean.getEndEntityProfile(endEntityProfileId)).andReturn(endEntityProfile);
        expect(endEntityProfile.getValue(EndEntityProfile.SENDNOTIFICATION, 0)).andReturn(EndEntityProfile.TRUE);
        replay(caSessionBean);
        replay(certificateProfileSessionBean);
        replay(endEntityProfileSessionBean);
        replay(endEntityProfile);

        EnrollPkcs10CertificateRequest request = new EnrollPkcs10CertificateRequest.Builder()
                .certificateRequest(csrRequest)
                .certificateProfileName(certificateProfileName)
                .endEntityProfileName(endEntityProfileName)
                .certificateAuthorityName(certificateAuthorityName)
                .username(username)
                .build();

        // when
        EndEntityInformation endEntityInformation = testClass.convertToEndEntityInformation(authenticationToken, request);

        // then
        String subjectDirectoryAttributes = endEntityInformation.getExtendedInformation().getSubjectDirectoryAttributes();
        assertTrue("Subject Directory attributes should contain date of birth",
                subjectDirectoryAttributes.contains("dateOfBirth=19840202") );
        assertTrue("Subject Directory attributes should contain gender",
                subjectDirectoryAttributes.contains("gender=F") );
        assertTrue("Subject Directory attributes should contain country of citizenship",
                subjectDirectoryAttributes.contains("countryOfCitizenship=EE") );
        assertTrue("Subject Directory attributes should contain country of residence",
                subjectDirectoryAttributes.contains("countryOfResidence=FR") );
        // Also check that we handled multiple extensions in the CSR
        assertEquals("Subject Alt Name should contain value", altNameString, endEntityInformation.getSubjectAltName());
    }

    private String generateCsrWithSubjectDirectoryAttributesAndSAN(final String dn, String dirAttrString, String altName) throws InvalidAlgorithmParameterException, IOException, OperatorCreationException {
        CryptoProviderTools.installBCProviderIfNotAvailable();
        final X500Name x509dn = new X500Name(dn);
        //this key is not used to issue any certifictate. It is used only to generate a csr that can be parsed
        final KeyPair keyPair = KeyTools.genKeys("secp256r1", "EC");

        // Create a P10 with extensions,
        ExtensionsGenerator extgen = new ExtensionsGenerator();

        // Subject Directory Attributes with dateOfBirth, gender, countryOfCitizenchip and countryOfResidence
        ASN1Encodable asn1Encodable = SubjectDirectoryAttributes.getAsn1Encodable(dirAttrString);
        extgen.addExtension(Extension.subjectDirectoryAttributes, false, asn1Encodable);
        // Subject Alternative Name with DNS and IP
        final GeneralNames san = CertTools.getGeneralNamesFromAltName(altName);
        extgen.addExtension(Extension.subjectAlternativeName, false, san);

        Extensions exts = extgen.generate();
        ASN1EncodableVector extensions = new ASN1EncodableVector();
        extensions.add(PKCSObjectIdentifiers.pkcs_9_at_extensionRequest);
        extensions.add(new DERSet(exts));

        // Complete the Attribute section of the request, the set (Attributes)
        // contains one sequence (Attribute)
        ASN1EncodableVector v = new ASN1EncodableVector();
        v.add(new DERSequence(extensions));
        DERSet attributes = new DERSet(v);

        PKCS10CertificationRequest pkcs10Request = CertTools.genPKCS10CertificationRequest("SHA256WithECDSA", x509dn, keyPair.getPublic(), attributes,
                keyPair.getPrivate(), null);
        byte[] pem = CertTools.getPEMFromCertificateRequest(pkcs10Request.getEncoded());
        String request = new String(pem, StandardCharsets.UTF_8);
        log.info("request:***" + request + "***");
        return request;
    }
    
    @Test
    public void shouldConvertToCorrectEndEntityInformationWithoutEmail() throws Exception {
        shouldConvertToCorrectEndEntityInformation(null);
    }
    
    @Test
    public void shouldConvertToCorrectEndEntityInformationWithEmail() throws Exception {
        shouldConvertToCorrectEndEntityInformation("random@random.rand");
    }

    public void shouldConvertToCorrectEndEntityInformation(String email) throws Exception {
        // given
        String endEntityProfileName = "eep1";
        int endEntityProfileId = 7;

        String certificateAuthorityName = "CA1";
        String certificateProfileName = "CP1";
        String username = "testuser";
        int certificateProfileId = 9;
        String subjectDn = CertTools.stringToBCDNString("CN=mydn"); 
        String name = "test123";
        int status = 20;
        String encodedValidity = "";
        int signedby = 1;

        X509Certificate mockX509Cert = EasyMock.mock(X509Certificate.class);

        X509Certificate[] certs = new X509Certificate[1];
        certs[0] = mockX509Cert;

        Collection<Certificate> certificatechain = new ArrayList<>();
        CAToken caToken = EasyMock.mock(CAToken.class);

        CAInfo caInfo = X509CAInfo.getDefaultX509CAInfo(subjectDn, name, status, certificateProfileId, encodedValidity, signedby, certificatechain, caToken);

        EndEntityProfile endEntityProfile = EasyMock.createNiceMock(EndEntityProfile.class);

        AuthenticationToken authenticationToken = EasyMock.mock(AuthenticationToken.class);

        expect(caSessionBean.getCAInfo(authenticationToken, certificateAuthorityName)).andReturn(caInfo);
        expect(certificateProfileSessionBean.getCertificateProfileId(certificateProfileName)).andReturn(certificateProfileId);
        expect(endEntityProfileSessionBean.getEndEntityProfileId(endEntityProfileName)).andReturn(endEntityProfileId);
        expect(endEntityProfileSessionBean.getEndEntityProfile(endEntityProfileId)).andReturn(endEntityProfile);
        expect(endEntityProfile.useAutoGeneratedPasswd()).andReturn(false);
        expect(endEntityProfile.getValue(EndEntityProfile.SENDNOTIFICATION, 0)).andReturn(EndEntityProfile.TRUE);

        replay(caSessionBean);
        replay(certificateProfileSessionBean);
        replay(endEntityProfileSessionBean);
        replay(endEntityProfile);
        
        EnrollPkcs10CertificateRequest.Builder builder = new EnrollPkcs10CertificateRequest.Builder()
                .certificateRequest(csr)
                .certificateProfileName(certificateProfileName)
                .endEntityProfileName(endEntityProfileName)
                .certificateAuthorityName(certificateAuthorityName)
                .username(username);

        if(email!=null) {
            builder.email(email);
        }
        
        EnrollPkcs10CertificateRequest request = builder.build();

        // when
        EndEntityInformation endEntityInformation = testClass.convertToEndEntityInformation(authenticationToken, request);

        // then
        assertEquals("The injected endentity profile id in 'given' section didnt get converted into result properly",
                endEntityProfileId, endEntityInformation.getEndEntityProfileId());

        assertEquals("The injected certificate profile id in 'given' section didnt get converted into result properly",
                certificateProfileId, endEntityInformation.getCertificateProfileId());

        assertEquals("End entiy DN got incorrectly parsed pkcs10 certificate request",
                "C=EE,ST=Alabama,L=tallinn,O=naabrivalve,CN=hello123server6", endEntityInformation.getDN());
        
        assertEquals("Email was not set during conversion.", email,endEntityInformation.getEmail());

        EasyMock.verify();
    }

    @Test
    public void shouldParseCorrectDn() {
        PKCS10CertificationRequest pkcs10CertificateRequest = CertTools.getCertificateRequestFromPem(csr);
        String actualResult = testClass.getSubjectDn(pkcs10CertificateRequest);
        String expectedResult = "C=EE,ST=Alabama,L=tallinn,O=naabrivalve,CN=hello123server6";
        assertEquals("End entiy DN got incorrectly parsed from pkcs10 certificate request", expectedResult, actualResult);
    }

    @Test
    public void shouldParseCorrectAn() {
        PKCS10CertificationRequest pkcs10CertificateRequest = CertTools.getCertificateRequestFromPem(csr);
        String actualResult = testClass.getSubjectAltName(pkcs10CertificateRequest);
        String expectedResult = "dNSName=somedns.com, iPAddress=192.168.1.7, dNSName=some.other.dns.com, directoryName=CN=Test\\,L=XX";
        assertEquals("Subject AN got incorrectly parsed from pkcs10 certificate request", expectedResult, actualResult);
    }
}
