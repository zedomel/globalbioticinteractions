package org.eol.globi.taxon;

import com.Ostermiller.util.CSVParse;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.eol.globi.domain.PropertyAndValueDictionary;
import org.eol.globi.domain.Term;
import org.eol.globi.domain.TermImpl;
import org.eol.globi.service.ResourceService;
import org.eol.globi.service.TermLookupService;
import org.eol.globi.service.TermLookupServiceException;
import org.eol.globi.util.CSVTSVUtil;
import org.eol.globi.util.InteractUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.StringReader;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public abstract class TermLookupServiceImpl implements TermLookupService {
    private static final Logger LOG = LoggerFactory.getLogger(TermLookupServiceImpl.class);

    private Map<String, List<Term>> mapping = null;
    private final ResourceService resourceService;

    protected abstract List<URI> getMappingURIList();

    protected abstract char getDelimiter();

    public TermLookupServiceImpl(ResourceService resourceService) {
        this.resourceService = resourceService;
    }

    @Override
    public List<Term> lookupTermByName(final String name) throws TermLookupServiceException {
        if (mapping == null) {
            buildMapping(getMappingURIList());
        }
        List<Term> terms = mapping.get(normalize(name));
        if (terms == null || terms.size() == 0) {
            // attempt mapping denormalized name/id
            terms = mapping.get(StringUtils.trim(name));
        }

        return terms == null ? new ArrayList<Term>() {{
            add(new TermImpl(PropertyAndValueDictionary.NO_MATCH, name));
        }} : terms;
    }

    private String normalize(String name) {
        return InteractUtil
                .removeQuotesAndBackslashes(StringUtils.lowerCase(name));
    }

    private void buildMapping(List<URI> uriList) throws TermLookupServiceException {
        mapping = new HashMap<>();

        for (URI uri : uriList) {
            try {
                String response = IOUtils.toString(resourceService.retrieve(uri), StandardCharsets.UTF_8);
                CSVParse parser = CSVTSVUtil.createExcelCSVParse(new StringReader(response));
                parser.changeDelimiter(getDelimiter());

                if (hasHeader()) {
                    parser = CSVTSVUtil.createLabeledCSVParser(parser);
                }
                String[] line;
                while ((line = parser.getLine()) != null) {
                    if (line.length < 4) {
                        LOG.info("line: [" + parser.getLastLineNumber() + "] in [" + uriList + "] contains less than 4 columns");
                    } else {
                        String sourceName = line[1];
                        String targetId = line[2];
                        String targetName = line[3];
                        if (StringUtils.isNotBlank(sourceName)
                                && StringUtils.isNotBlank(targetId)
                                && StringUtils.isNotBlank(targetName)) {
                            List<Term> terms = mapping
                                    .computeIfAbsent(normalize(sourceName), k -> new ArrayList<>());
                            terms.add(new TermImpl(targetId, targetName));
                        }
                    }
                }
            } catch (IOException e) {
                throw new TermLookupServiceException("failed to retrieve mapping from [" + uriList + "]", e);
            }
        }
    }

    protected abstract boolean hasHeader();

    public void shutdown() {

    }
}
