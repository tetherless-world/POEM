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
    let aboutRequest = null;
    let aboutRequestId = 0;

    new Choices(document.getElementById('explore-concepts'), {
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
    explore.addEventListener('click', async () => {
        if (instrument.disabled || !instrument.value) return;
        if (aboutRequest) aboutRequest.abort();
        const request = new AbortController();
        aboutRequest = request;
        const requestId = ++aboutRequestId;
        const selectedUri = instrument.value;
        detailsTitle.textContent = instrument.selectedOptions[0].textContent;
        document.getElementById('instrument-details-language').textContent = language.selectedOptions[0].textContent;
        document.getElementById('instrument-details-informant').textContent = informant.selectedOptions[0].textContent;
        exportUris.forEach(input => { input.value = instrument.value; });
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
    });
    updateInstruments();
})();
