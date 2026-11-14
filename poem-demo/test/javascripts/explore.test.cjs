const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const test = require('node:test');
const vm = require('node:vm');

// Minimal DOM surface used by explore.js, so the collection handoff can run without a browser.
class Element {
    constructor() {
        this.options = [];
        this.selectedOptions = [];
        this.children = [];
        this.dataset = {};
        this.value = '';
        this.listeners = {};
        this.attributes = {};
    }
    addEventListener(event, listener) { this.listeners[event] = listener; }
    setAttribute(key, value) { this.attributes[key] = value; }
    replaceChildren(...children) { this.children = children; }
    append(...children) { this.children.push(...children); }
    appendChild(child) { this.children.push(child); }
    querySelectorAll() { return []; }
    focus() { this.focused = true; }
}

test('concept Explore loads collection details and lets users continue to instrument selection', async () => {
    const elements = new Map();
    const get = id => {
        if (!elements.has(id)) elements.set(id, new Element());
        return elements.get(id);
    };
    const collectionUri = 'http://example.org/collection';
    const termUri = 'http://snomed.info/id/1';
    const requests = [];
    let loadDetails = async () => ({
        uri: collectionUri, label: 'Example collection', definition: 'A collection description',
        notation: 'EX', instrumentCount: 2,
        scales: [{uri: 'http://example.org/scale', label: 'Anxiety scale'}],
        languages: [{uri: 'http://example.org/en', label: 'English'}],
    });
    get('explore-concepts').selectedOptions = [{value: termUri, textContent: 'Anxiety'}];
    get('concept-panel').dataset.searchUrl = '/api/instrument-collections/search';
    get('collection-details').dataset.aboutUrl = '/api/instrument-collections/about';
    const context = {
        document: {getElementById: get, createElement: () => new Element()},
        window: {location: {href: 'http://localhost/explore'}},
        URL,
        AbortController,
        Option: class { constructor(text, value) { this.textContent = text; this.value = value; } },
        Choices: class {},
        bootstrap: {Collapse: {getOrCreateInstance: panel => ({show() {
            get('explore-panels').listeners['show.bs.collapse']();
            panel.listeners['shown.bs.collapse']();
        }})}},
        fetch: async url => {
            requests.push(url);
            if (url.pathname.endsWith('/about')) return {ok: true, json: () => loadDetails()};
            return {ok: true, json: async () => [{
                uri: collectionUri, label: 'Example collection',
                matches: [{termUri, matchType: 'scale'}, {termUri, matchType: 'item'}],
            }]};
        },
    };
    vm.runInNewContext(fs.readFileSync(path.join(__dirname, '../../public/javascripts/explore.js'), 'utf8'), context);

    await get('concept-search-button').listeners.click();

    assert.equal(requests[0].pathname, '/api/instrument-collections/search');
    assert.equal(requests[0].searchParams.get('term'), termUri);
    const [title, reasons, button] = get('concept-search-results').children[0].children;
    assert.equal(title.textContent, 'Example collection');
    assert.deepEqual(reasons.children.map(chip => chip.textContent), ['Anxiety (scale)', 'Anxiety (item)']);
    assert.equal(get('concept-search-status').textContent, '1 matching instrument collection found.');

    get('explore-language').value = 'en';
    get('explore-informant').value = 'youth';
    await button.listeners.click();

    assert.equal(requests[1].pathname, '/api/instrument-collections/about');
    assert.equal(requests[1].searchParams.get('uri'), collectionUri);
    assert.equal(get('collection-details').hidden, false);
    assert.equal(get('collection-details-title').focused, true);
    assert.equal(get('collection-description').textContent, 'A collection description');
    assert.equal(get('collection-notation').textContent, 'EX');
    assert.equal(get('collection-instrument-count').textContent, '2 instruments');
    assert.equal(get('collection-scales').children[0].textContent, 'Anxiety scale');
    assert.equal(get('collection-languages').children[0].textContent, 'English');
    assert.equal(get('instrument-details').hidden, true);
    assert.equal(get('collection-details-content').attributes['aria-busy'], 'false');

    get('collection-choose-instrument').listeners.click();

    assert.equal(get('explore-collection').value, collectionUri);
    assert.equal(get('explore-language').value, '');
    assert.equal(get('explore-informant').value, '');
    assert.equal(get('explore-collection').focused, true);
    assert.equal(get('instrument-details').hidden, true);
    assert.equal(get('collection-details').hidden, true);
    assert.equal(requests.length, 2);

    loadDetails = async () => { throw new Error('Offline'); };
    await button.listeners.click();
    assert.equal(get('collection-details-retry').hidden, false);
    assert.equal(get('collection-details-content').hidden, true);
    assert.equal(get('collection-details-content').attributes['aria-busy'], 'false');

    loadDetails = async () => ({uri: collectionUri, instrumentCount: 0, scales: [], languages: []});
    await get('collection-details-retry').listeners.click();
    assert.equal(get('collection-details-retry').hidden, true);
    assert.equal(get('collection-instrument-count').textContent, '0 instruments');
    assert.equal(get('collection-choose-instrument').disabled, true);
    assert.equal(get('collection-notation').textContent, 'Not recorded');
    assert.equal(get('collection-description').textContent, 'No description is recorded for this collection.');
    assert.equal(get('collection-scales').children.length, 0);
    assert.equal(get('collection-languages-summary').textContent, 'No languages are recorded for instruments in this collection.');

    let resolveDetails;
    const delayedDetails = new Promise(resolve => { resolveDetails = resolve; });
    loadDetails = () => delayedDetails;
    const pending = button.listeners.click();
    await Promise.resolve();
    get('explore-concepts').listeners.change();
    resolveDetails({uri: collectionUri, label: 'Stale result', instrumentCount: 9, scales: [], languages: []});
    await pending;
    assert.equal(get('collection-details').hidden, true);
    assert.notEqual(get('collection-details-title').textContent, 'Stale result');
});
