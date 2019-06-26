package com.scienceminer.lookup.web.resource;

import com.scienceminer.lookup.data.MatchingDocument;
import com.scienceminer.lookup.storage.LookupEngine;
import com.scienceminer.lookup.storage.lookup.*;
import org.easymock.EasyMock;
import org.junit.Before;
import org.junit.Test;

import javax.ws.rs.container.AsyncResponse;

import static org.easymock.EasyMock.*;

public class LookupControllerTest {

    LookupController target;
    private LookupEngine lookupEngine;
    private AsyncResponse mockedAsyncResponse;
    private OALookup mockOALookup;
    private IstexIdsLookup mockIstexLookup;
    private PMIdsLookup mockPmidsLookup;
    private MetadataMatching mockMetadataMatching;
    private MetadataLookup mockMetadataLookup;

    @Before
    public void setUp() throws Exception {
        target = new LookupController();

        mockOALookup = EasyMock.mock(OALookup.class);
        mockIstexLookup = EasyMock.mock(IstexIdsLookup.class);
        mockPmidsLookup = EasyMock.mock(PMIdsLookup.class);
        mockMetadataMatching = EasyMock.mock(MetadataMatching.class);
        mockMetadataLookup = EasyMock.mock(MetadataLookup.class);

        lookupEngine = new LookupEngine();
        lookupEngine.setOaDoiLookup(mockOALookup);
        lookupEngine.setIstexLookup(mockIstexLookup);
        lookupEngine.setPmidLookup(mockPmidsLookup);
        lookupEngine.setMetadataLookup(mockMetadataLookup);
        lookupEngine.setMetadataMatching(mockMetadataMatching);

        mockedAsyncResponse = EasyMock.createMock(AsyncResponse.class);
        target.setLookupEngine(lookupEngine);
    }

    /**
     * fatcat correspond to a document, postValidation is passing
     * -> returning the json corresponding to this fatcat
     */
    @Test
    public void getByQuery_fatcatExists_passingPostValidation_shouldReturnJSONBody() {
        final String myFatcat = "release_asdf";
        final boolean postValidate = true;
        final String atitle = "atitle";
        final String firstAuthor = "firstAuthor";

        final String jsonOutput = "{\"ident\":\"" + myFatcat + "\",\"title\":\"" + atitle + "\",\"contribs\":[{\"given_name\":\"Alexander Yu\",\"surname\":\"" + firstAuthor + "\",\"index\":0}]}";

        final MatchingDocument response = new MatchingDocument(myFatcat, jsonOutput);
        expect(mockMetadataLookup.retrieveByFatcat(myFatcat)).andReturn(response);
        expect(mockIstexLookup.retrieveByDoi(null)).andReturn(null);
        expect(mockPmidsLookup.retrieveIdsByDoi(null)).andReturn(null);
        expect(mockOALookup.retrieveOALinkByDoi(null)).andReturn(null);
        expect(mockedAsyncResponse.resume(response.getJsonObject())).andReturn(true);

        replay(mockMetadataLookup, mockedAsyncResponse, mockPmidsLookup, mockOALookup, mockIstexLookup, mockMetadataMatching);
        target.getByQuery(myFatcat, null, null, null, null, null, firstAuthor, atitle,
                postValidate, null, null, null, null, null, mockedAsyncResponse);

        verify(mockMetadataLookup, mockedAsyncResponse, mockPmidsLookup, mockOALookup, mockIstexLookup, mockMetadataMatching);
    }

    /**
     * fatcat correspond to a document, postValidation is not passing
     * -> returning the json corresponding to to the result of title + first author
     */
    @Test
    public void getByQuery_fatcatExists_NotPassingPostValidation_shouldReturnJSONFromTitleFirstAuthor() {
        final String myFatcat = "release_asdf";
        final boolean postValidate = true;
        final String atitle = "atitle";
        final String firstAuthor = "firstAuthor";

        final String jsonOutput = "{\"ident\":\"" + myFatcat + "\",\"title\":\"" + atitle + "12312312313\",\"contribs\":[{\"given_name\":\"Alexander Yu\",\"surname\":\"" + firstAuthor + "\",\"index\":0}]}";
        final MatchingDocument response = new MatchingDocument(myFatcat, jsonOutput);

        expect(mockMetadataLookup.retrieveByFatcat(myFatcat)).andReturn(response);
        mockMetadataMatching.retrieveByMetadataAsync(eq(atitle), eq(firstAuthor), anyObject());
//        expect(mockIstexLookup.retrieveByDoi(myFatcat)).andReturn(null);
//        expect(mockPmidsLookup.retrieveIdsByDoi(myFatcat)).andReturn(null);
//        expect(mockedAsyncResponse.resume(response.getJsonObject())).andReturn(true);

//        expect(lookupEngine.retrieveByDoi(myFatcat, postValidate, firstAuthor, atitle))
//                .andThrow(new NotFoundException("Did not pass the validation"));
//        lookupEngine.retrieveByArticleMetadataAsync(eq(atitle), eq(firstAuthor), eq(postValidate), anyObject());
//        expect(mockedAsyncResponse.resume(response)).andReturn(true);

        replay(mockMetadataLookup, mockedAsyncResponse, mockPmidsLookup, mockOALookup, mockIstexLookup, mockMetadataMatching);
        target.getByQuery(myFatcat, null, null, null, null, null, firstAuthor, atitle,
                postValidate, null, null, null, null, null, mockedAsyncResponse);

        verify(mockMetadataLookup, mockedAsyncResponse, mockPmidsLookup, mockOALookup, mockIstexLookup, mockMetadataMatching);
    }

    /**
     * fatcat doesn't match
     * -> using title and first author -> Match
     */
    @Test
    public void getByQuery_fatcatExists_WithPostvalidation_shouldReturnJSONFromTitleFirstAuthor() {
        final String myFatcat = "myFatcat";
        final boolean postValidate = true;
        final String atitle = "atitle";
        final String firstAuthor = "firstAuthor";

        final String jsonOutput = "{\"ident\":\"" + myFatcat + "\",\"title\":\"" + atitle + "12312312313\",\"contribs\":[{\"given_name\":\"Alexander Yu\",\"surname\":\"" + firstAuthor + "\",\"index\":0}]}";
        final MatchingDocument response = new MatchingDocument(myFatcat, jsonOutput);

        expect(mockMetadataLookup.retrieveByFatcat(myFatcat)).andReturn(new MatchingDocument());
        mockMetadataMatching.retrieveByMetadataAsync(eq(atitle), eq(firstAuthor), anyObject());

        replay(mockMetadataLookup, mockedAsyncResponse, mockPmidsLookup, mockOALookup, mockIstexLookup, mockMetadataMatching);
        target.getByQuery(myFatcat, null, null, null, null, null, firstAuthor, atitle,
                postValidate, null, null, null, null, null, mockedAsyncResponse);

        verify(mockMetadataLookup, mockedAsyncResponse, mockPmidsLookup, mockOALookup, mockIstexLookup, mockMetadataMatching);
    }
}
