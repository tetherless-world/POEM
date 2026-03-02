
import './App.css'
import { Routes, Route} from "react-router-dom"
import Home from "./pages/Home";
import NavBar from './components/Navbar';
import Footer from './components/footer';
import MeasureOveriew from './pages/MeasureOverview';
import InstrumentPage from './pages/measures/InstrumentPage';
export default function App() {
  return (
    <div className='flex flex-col min-h-screen'>
    <NavBar />
    <main className='w-full grow mx-auto'>
    <Routes>
      
      <Route path = "/" element = {<Home />} />
      <Route path = "measureoverview" element = {<MeasureOveriew/>} />
      <Route path="/measures/:id" element={<InstrumentPage />} />
    </Routes>
    </main>
     <Footer/>
    </div>
  )
}