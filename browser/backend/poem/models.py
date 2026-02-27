from typing import List, Optional
from pydantic import BaseModel

class Node(BaseModel):
    uri: str
    label: Optional[str] = None
class Ref(Node):
    pass
class Language(Node):
    notation: Optional[str] = None
    country_code: Optional[str] = None
class InstrumentCollection(Node):
    definition: Optional[str] = None
    instruments: List[Ref] = []

class Instrument(Node):
    informat:Optional[Ref] = None
    language: Optional[Language] = None
    deprecated: Optional[int] = None
    items: List[Ref] = []
    components: List[Ref] = []

class Item(Node):
    value: Optional[int] = None
    item_stem: Optional[Ref] = None
    codebook: Optional[Ref] = None

class item_stem(Node):
    item_stem_concept: List[Ref] = []

class scale(Node):
    notation: Optional[str] = None

class agent(Node):
    person: Optional[Ref] = None
class activity(Node):
    methods: List[Ref] = []
    used: List[Ref] = []
    generated: Optional[Ref]
    agent: Optional[Ref] = None
