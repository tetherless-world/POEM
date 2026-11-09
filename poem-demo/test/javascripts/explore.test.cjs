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

test('concept results show collections and Explore selects a collection instead of loading instrument details', async () => {
    const elements = new Map();
    const get = id => {
        if (!elements.has(id)) elements.set(id, new Element());
        return elements.get(id);
    };
    const collectionUri = 'http://example.org/collection';
    const termUri = 'http://snomed.info/id/1';
    const requests = [];
    get('explore-concepts').selectedOptions = [{value: termUri, textContent: 'Anxiety'}];
    get('concept-panel').dataset.searchUrl = '/api/instrument-collections/search';
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
    button.listeners.click();

    assert.equal(get('explore-collection').value, collectionUri);
    assert.equal(get('explore-language').value, '');
    assert.equal(get('explore-informant').value, '');
    assert.equal(get('explore-collection').focused, true);
    assert.equal(get('instrument-details').hidden, true);
    assert.equal(requests.length, 1);
});
