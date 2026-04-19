
import './App.css'
import { Routes, Route} from "react-router-dom"
import Home from "./pages/Home";
import NavBar from './components/Navbar';
import Footer from './components/footer';
import InstrumentOverview from './pages/InstrumentOverview';
import InstrumentPage from './pages/instruments/InstrumentPage';
import InstrumentList from './pages/instruments/list/page';
import IndividualInstrumentPage from './pages/instruments/individual/page';
import Scales from './pages/Scales';
import Glossary from './pages/Glossary';
import Search from './pages/Search';
export default function App() {
  return (
    <div className='flex flex-col min-h-screen'>
    <NavBar />
    <main className='w-full grow mx-auto m-0'>
          {/*<SummarizePage/>*/}
    <Routes>
       
      <Route path = "/" element = {<Home />} />
      <Route path = "/instruments" element = {<InstrumentOverview/>} />
      <Route path="/instruments/:id" element={<InstrumentPage />} />
      <Route path="/instruments/list/:id" element={<InstrumentList />} />
      <Route path ="/Scales" element={<Scales />} />
      <Route path= "/instruments/individual/:id" element={<IndividualInstrumentPage />} />
      <Route path = "/Glossary" element = {<Glossary/>} />
      <Route path = "/Search/" element = {<Search/>} /> 
    </Routes>
    </main>
     <Footer/>
 
    </div>
  )
}