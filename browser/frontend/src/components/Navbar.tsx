import { NavLink } from "react-router-dom";

export default function NavBar() {
    return (
        <header className= "w-full border-b border-black/10 bg-white shadow-sm">
        <div className=" max-w-7xl mx-auto px-4 py-4 flex items-center justify-between gap-6">

            <div className="flex items-center gap-3">
                <h2 className="text-xl font-semibold text-gray-800 whitespace-nowrap">
                    <span className="text-amber-500">Poem</span> Mental Health Measures
                </h2>
            </div>

            <nav className="hidden md:flex items-center gap-6 text-sm text-gray-600">
                <NavLink to ="/"  className="hover:text-gray-900 transition" >Home</NavLink>
                <a className="hover:text-gray-900 transition" href="#">About</a>
                <a className="hover:text-gray-900 transition" href="#">User Guide</a>
                <a className="hover:text-gray-900 transition" href="#">Glossary</a>
                <NavLink to="measureoverview" className="hover:text-gray-900 transition" >Measures</NavLink>
                <a className="hover:text-gray-900 transition" href="#">Contribute</a>
                <a className="hover:text-gray-900 transition" href="#">Contact Us</a>
            </nav>

            <div className="flex items-center gap-3">
                <button
                    className="hidden md:inline-flex px-3 py-2 text-sm rounded-lg border border-gray-200 hover:bg-gray-50 transition">
                    Language
                </button>
                <button
                    className="hidden md:inline-flex px-3 py-2 text-sm rounded-lg border border-gray-200 hover:bg-gray-50 transition">
                    Dark Mode
                </button>
                <button className="px-3 py-2 text-sm rounded-lg border border-gray-200 hover:bg-gray-50 transition">
                    ?
                </button>

                <button
                    className="md:hidden px-3 py-2 text-sm rounded-lg border border-gray-200 hover:bg-gray-50 transition">
                    Menu
                </button>
            </div>

        </div>
    </header>
    )
}