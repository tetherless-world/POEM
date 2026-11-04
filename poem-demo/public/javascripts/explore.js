(() => {
    'use strict';

    const collection = document.getElementById('explore-collection');
    const language = document.getElementById('explore-language');
    const informant = document.getElementById('explore-informant');
    const instrument = document.getElementById('explore-instrument');
    const status = document.getElementById('instrument-selection-status');
    const instruments = Array.from(instrument.options).filter(option => option.value);
    const explore = document.getElementById('explore-button');
    const details = document.getElementById('instrument-details');
    const detailsTitle = document.getElementById('instrument-details-title');
    const exportUris = details.querySelectorAll('[data-selected-instrument]');
    const aboutStatus = document.getElementById('instrument-about-status');
    const aboutContent = document.getElementById('instrument-about-content');
    const scalesSummary = document.getElementById('instrument-scales-summary');
    const scales = document.getElementById('instrument-scales');
    const itemCount = document.getElementById('instrument-item-count');
    const itemUnit = document.getElementById('instrument-item-unit');
    const concepts = document.getElementById('explore-concepts');
    const conceptPanel = document.getElementById('concept-panel');
    const searchButton = document.getElementById('concept-search-button');
    const searchStatus = document.getElementById('concept-search-status');
    const searchResults = document.getElementById('concept-search-results');
    let aboutRequest = null;
    let aboutRequestId = 0;
    let searchRequest = null;
    let searchRequestId = 0;
    let detailSource = null;

    new Choices(concepts, {
        allowHTML: false,
        removeItemButton: true,
        searchEnabled: true,
        placeholderValue: 'Search and select concepts',
        searchPlaceholderValue: 'Type a symptom or experience',
        searchFields: ['label'],
        shouldSort: false,
        itemSelectText: '',
    });

    function resetDetails() {
        aboutRequestId += 1;
        if (aboutRequest) aboutRequest.abort();
        aboutRequest = null;
        aboutContent.setAttribute('aria-busy', 'false');
        details.hidden = true;
        detailSource = null;
        explore.disabled = instrument.disabled || !instrument.value;
        exportUris.forEach(input => { input.value = ''; });
    }

    function updateInstruments() {
        const ready = Boolean(collection.value && language.value && informant.value);
        const matches = ready ? instruments.filter(option =>
            option.dataset.collections.split(' ').includes(collection.value) &&
            option.dataset.language === language.value &&
            option.dataset.informants.split(' ').includes(informant.value)
        ) : [];

        const placeholder = !ready ? 'Choose collection, language and respondent first' :
            matches.length ? 'Choose an instrument' : 'No matching instruments';
        instrument.replaceChildren(new Option(placeholder, ''), ...matches.map(option => option.cloneNode(true)));
        instrument.value = '';
        instrument.disabled = !ready || matches.length === 0;
        status.textContent = !ready ? 'Choose a collection, language and respondent to see matching instruments.' :
            matches.length ? `${matches.length} matching instrument${matches.length === 1 ? '' : 's'} available.` :
                'No instruments match this collection, language and respondent. Try another selection.';
        resetDetails();
    }

    collection.addEventListener('change', updateInstruments);
    language.addEventListener('change', updateInstruments);
    informant.addEventListener('change', updateInstruments);
    instrument.addEventListener('change', resetDetails);
    async function showInstrumentDetails(selected, source) {
        if (aboutRequest) aboutRequest.abort();
        const request = new AbortController();
        aboutRequest = request;
        const requestId = ++aboutRequestId;
        const selectedUri = selected.uri;
        detailSource = source;
        detailsTitle.textContent = selected.label || selectedUri;
        const option = instruments.find(candidate => candidate.value === selectedUri);
        const languageLabel = Array.from(language.options).find(candidate =>
            candidate.value && candidate.value === option?.dataset.language)?.textContent || '';
        const respondentLabels = Array.from(informant.options).filter(candidate =>
            candidate.value && option?.dataset.informants.split(' ').includes(candidate.value)
        ).map(candidate => candidate.textContent).join(', ');
        const languageChip = document.getElementById('instrument-details-language');
        const respondentChip = document.getElementById('instrument-details-informant');
        languageChip.textContent = languageLabel;
        languageChip.hidden = !languageLabel;
        respondentChip.textContent = respondentLabels;
        respondentChip.hidden = !respondentLabels;
        exportUris.forEach(input => { input.value = selectedUri; });
        details.hidden = false;
        detailsTitle.focus();
        aboutStatus.textContent = 'Loading scales and item count…';
        aboutContent.setAttribute('aria-busy', 'true');
        scalesSummary.textContent = '';
        scales.replaceChildren();
        itemCount.textContent = '—';
        itemUnit.textContent = 'items';

        try {
            const url = new URL(details.dataset.aboutUrl, window.location.href);
            url.searchParams.set('uri', selectedUri);
            const response = await fetch(url, {
                headers: {Accept: 'application/json'},
                signal: request.signal,
            });
            if (!response.ok) throw new Error('Instrument details request failed');
            const about = await response.json();
            if (requestId !== aboutRequestId) return;

            scalesSummary.textContent = about.scales.length ?
                `${about.scales.length} scale${about.scales.length === 1 ? '' : 's'}:` :
                'No scales are recorded for this instrument.';
            scales.replaceChildren(...about.scales.map(scale => {
                const chip = document.createElement('span');
                chip.className = 'proof-chip';
                chip.textContent = scale.label || scale.uri;
                return chip;
            }));
            itemCount.textContent = about.itemCount.toLocaleString();
            itemUnit.textContent = about.itemCount === 1 ? 'item' : 'items';
            aboutStatus.textContent = 'Scales and item count loaded.';
        } catch (error) {
            if (requestId !== aboutRequestId || error.name === 'AbortError') return;
            aboutStatus.textContent = 'Unable to load scales and item count. Select Explore to try again.';
        } finally {
            if (requestId === aboutRequestId) {
                aboutContent.setAttribute('aria-busy', 'false');
                aboutRequest = null;
            }
        }
    }

    explore.addEventListener('click', () => {
        if (instrument.disabled || !instrument.value) return;
        return showInstrumentDetails({uri: instrument.value, label: instrument.selectedOptions[0].textContent}, 'known');
    });

    function resetConceptSearch() {
        searchRequestId += 1;
        if (searchRequest) searchRequest.abort();
        searchRequest = null;
        searchResults.replaceChildren();
        searchResults.setAttribute('aria-busy', 'false');
        searchButton.disabled = concepts.disabled || concepts.selectedOptions.length === 0;
        searchStatus.textContent = searchButton.disabled ?
            'Select one or more concepts to find matching instruments.' :
            'Find instruments that match any selected concept through their scales or items.';
        if (detailSource === 'concept') resetDetails();
    }

    concepts.addEventListener('change', resetConceptSearch);
    document.getElementById('explore-panels').addEventListener('show.bs.collapse', resetDetails);
    searchButton.addEventListener('click', async () => {
        const terms = Array.from(concepts.selectedOptions);
        if (concepts.disabled || !terms.length) return;
        if (searchRequest) searchRequest.abort();
        const request = new AbortController();
        searchRequest = request;
        const requestId = ++searchRequestId;
        const termLabels = new Map(terms.map(option => [option.value, option.textContent]));
        resetDetails();
        searchButton.disabled = true;
        searchResults.replaceChildren();
        searchResults.setAttribute('aria-busy', 'true');
        searchStatus.textContent = 'Searching instruments…';
        try {
            const url = new URL(conceptPanel.dataset.searchUrl, window.location.href);
            terms.forEach(option => url.searchParams.append('term', option.value));
            const response = await fetch(url, {headers: {Accept: 'application/json'}, signal: request.signal});
            if (!response.ok) throw new Error('Instrument search failed');
            const matches = await response.json();
            if (requestId !== searchRequestId) return;
            const rows = matches.map(match => {
                const row = document.createElement('li');
                row.className = 'list-group-item py-3';
                const title = document.createElement('h3');
                title.className = 'h5';
                title.textContent = match.label || match.uri;
                const reasons = document.createElement('div');
                reasons.className = 'd-flex flex-wrap gap-2 mb-3';
                match.matches.forEach(reason => {
                    const chip = document.createElement('span');
                    chip.className = 'proof-chip';
                    const label = termLabels.get(reason.termUri) || reason.termUri;
                    chip.textContent = `${label} (${reason.matchType === 'scale' ? 'scale' : 'item'})`;
                    reasons.appendChild(chip);
                });
                const button = document.createElement('button');
                button.type = 'button';
                button.className = 'btn btn-outline-primary';
                button.textContent = 'Explore';
                button.setAttribute('aria-controls', 'instrument-details');
                button.setAttribute('aria-label', `Explore ${match.label || match.uri}`);
                button.addEventListener('click', () => showInstrumentDetails(match, 'concept'));
                row.append(title, reasons, button);
                return row;
            });
            searchResults.replaceChildren(...rows);
            searchStatus.textContent = matches.length ?
                `${matches.length} matching instrument${matches.length === 1 ? '' : 's'} found.` :
                'No instruments match the selected concepts. Try another selection.';
        } catch (error) {
            if (requestId !== searchRequestId || error.name === 'AbortError') return;
            searchStatus.textContent = 'Unable to search instruments. Select Find instruments to try again.';
        } finally {
            if (requestId === searchRequestId) {
                searchRequest = null;
                searchResults.setAttribute('aria-busy', 'false');
                searchButton.disabled = concepts.disabled || concepts.selectedOptions.length === 0;
            }
        }
    });
    updateInstruments();
    resetConceptSearch();
})();
