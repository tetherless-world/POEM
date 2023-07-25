---
title: Demonstration
layout: single
sidebar:
        nav: "docs"
---

### Static Demonstration

<iframe src="https://docs.google.com/presentation/d/e/2PACX-1vQB2iVIhOzgoT49p5BcgfLqKalIhZlcHrcVwjE6WOZqdZcXqSoN38uIXkkVh4ht-qvnPD0mnQhkWA0k/embed?start=false&loop=true&delayms=30000" frameborder="0" width="960" height="569" allowfullscreen="true" mozallowfullscreen="true" webkitallowfullscreen="true"></iframe>

### Queries

#### Prefixes

```sparql
PREFIX owl: <http://www.w3.org/2002/07/owl#>
PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
PREFIX hasco: <http://hadatac.org/ont/hasco#>
PREFIX vstoi: <http://hadatac.org/ont/vstoi#>
PREFIX poem: <http://purl.org/ontology/POEM#>
PREFIX poem-rcads: <http://purl.org/ontology/POEM-RCADS#>
PREFIX sio: <http://semanticscience.org/resource/>
PREFIX snomed: <http://purl.bioontology.org/ontology/SNOMEDCT/>
```

#### Query 1: What does the RCADS-47 measure?

```sparql
SELECT ?condition (STR(?lab) AS ?label) WHERE {
	poem-rcads:RCADS47Questionnaire poem:hasScale ?scale .
	?scale poem:isAboutCondition ?condition .
?condition rdf:type poem:Condition .
	?condition rdfs:label ?label .
}
```
The RCADS-47 has 6 different scales that indicate the following conditions: anxiety disorder, panic disorder, separation anxiety, obsessive compulsive disorder, social phobia, and major depressive disorder.

#### Query 2: Who may fill out the RCADS-47?

```sparql
SELECT ?respondent WHERE {
	?respondent rdf:type sio:SIO_000485 .
	poem-rcads:RCADS47Questionnaire poem:hasPossibleRespondent ?respondent .
}
```

The RCADS-47 can be filled out either by a youth or their caregiver.

#### Query 3: Does the RCADS-47 have a shorter version, to be given more frequently?

```sparql
SELECT ?shorterQuestionnaire WHERE {
	?shorterQuestionnaire rdf:type vstoi:Questionnaire .
	?shorterQuestionnaire sio:SIO_000244 poem-rcads:RCADS47Questionnaire .
}
```

The RCADS-25 is a shorter derivative questionnaire of the RCADS-47.

#### Query 4: Does the RCADS-25 relate to the RCADS-47? Do they have questions in common?

```sparql
SELECT ?question (STR(?lab) AS ?label) WHERE {
	?question rdf:type vstoi:QuestionnaireItem .
	poem-rcads:RCADS25Questionnaire vstoi:hasItem ?question .
	poem-rcads:RCADS47Questionnaire vstoi:hasItem ?question .
	?question rdfs:label ?label .
}
```

The RCADS-47 and RCADS-25 have 25 questions in common, meaning that the RCADS-25 contains a subset of questions from the RCADS-47.

#### Query 5: Do any questions in the RCADS-47 assess worry about social performance?

```sparql
SELECT ?question (STR(?lab) AS ?label) WHERE {
        ?question rdf:type vstoi:QuestionnaireItem .
        poem-rcads:RCADS47Questionnaire vstoi:hasItem ?question .
        ?question poem:detects snomed:247825008 .
	?question rdfs:label ?label .
}
```

The RCADS-47 has seven questions that assess anxiety about behavior or performance.
