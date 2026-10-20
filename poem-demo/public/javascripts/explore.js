(() => {
    'use strict';

    const language = document.getElementById('explore-language');
    const informant = document.getElementById('explore-informant');
    const instrument = document.getElementById('explore-instrument');
    const status = document.getElementById('instrument-selection-status');
    const instruments = Array.from(instrument.options).filter(option => option.value);
    const explore = document.getElementById('explore-button');
    const details = document.getElementById('instrument-details');
    const detailsTitle = document.getElementById('instrument-details-title');
    const exportUris = details.querySelectorAll('[data-selected-instrument]');

    function resetDetails() {
        details.hidden = true;
        explore.disabled = instrument.disabled || !instrument.value;
        exportUris.forEach(input => { input.value = ''; });
    }

    function updateInstruments() {
        const ready = Boolean(language.value && informant.value);
        const matches = ready ? instruments.filter(option =>
            option.dataset.language === language.value &&
            option.dataset.informants.split(' ').includes(informant.value)
        ) : [];

        const placeholder = !ready ? 'Choose language and respondent first' :
            matches.length ? 'Choose an instrument' : 'No matching instruments';
        instrument.replaceChildren(new Option(placeholder, ''), ...matches.map(option => option.cloneNode(true)));
        instrument.value = '';
        instrument.disabled = !ready || matches.length === 0;
        status.textContent = !ready ? 'Choose a language and respondent to see matching instruments.' :
            matches.length ? `${matches.length} matching instrument${matches.length === 1 ? '' : 's'} available.` :
                'No instruments match this language and respondent. Try another selection.';
        resetDetails();
    }

    language.addEventListener('change', updateInstruments);
    informant.addEventListener('change', updateInstruments);
    instrument.addEventListener('change', resetDetails);
    explore.addEventListener('click', () => {
        if (instrument.disabled || !instrument.value) return;
        detailsTitle.textContent = instrument.selectedOptions[0].textContent;
        document.getElementById('instrument-details-language').textContent = language.selectedOptions[0].textContent;
        document.getElementById('instrument-details-informant').textContent = informant.selectedOptions[0].textContent;
        exportUris.forEach(input => { input.value = instrument.value; });
        details.hidden = false;
        detailsTitle.focus();
    });
    updateInstruments();
})();
