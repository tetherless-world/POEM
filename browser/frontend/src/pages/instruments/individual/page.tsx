import { useParams } from "react-router-dom";
type Params = {
  id: string;
};
export default function InstrumentPage()
{   
    const { id } = useParams<Params>();

    return (<div>
        <h1>{id}</h1>
    </div>)
}