
import './App.css'
import { Routes, Route} from "react-router-dom"
import Home from "./pages/Home";
import NavBar from './components/Navbar';
import Footer from './components/footer';
import MeasureOveriew from './pages/MeasureOverview';
export default function App() {
  return (
    <div className='min-h-screen'>
    <NavBar />
    <main className='w-full mx-auto'>
    <Routes>
      
      <Route path = "/" element = {<Home />} />
      <Route path = "measureoverview" element = {<MeasureOveriew/>} />
    </Routes>
    </main>
     <Footer/>
    </div>
  )
}