
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
import SummarizePage from './components/SummarizePage';
export default function App() {
  return (
    <div className='flex flex-col min-h-screen'>
    <NavBar />
    <main className='w-full grow mx-auto'>
          <SummarizePage/>
    <Routes>
      
      <Route path = "/" element = {<Home />} />
      <Route path = "instrumentoverview" element = {<InstrumentOverview/>} />
      <Route path="/instruments/:id" element={<InstrumentPage />} />
      <Route path="/instruments/list/:id" element={<InstrumentList />} />
      <Route path ="/scales" element={<Scales />} />
      <Route path= "/instruments/individual/:id" element={<IndividualInstrumentPage />} />
    </Routes>
    </main>
     <Footer/>
 
    </div>
  )
}