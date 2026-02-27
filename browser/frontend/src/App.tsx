import { useState, useEffect } from 'react'
import reactLogo from './assets/react.svg'
import viteLogo from '/vite.svg'
import './App.css'

export default function App() {
  const [status, setStatus] = useState<string>("loading...");

  useEffect(() => {
    const run = async (): Promise<void> => {
      const res: Response = await fetch("/api/health");
      const data: { status: string } = await res.json();
      setStatus(data.status);
    };
    void run();
  }, []);

  return (
    <div className="min-h-screen grid place-items-center bg-zinc-950 text-zinc-100">
      <div className="rounded-2xl border border-zinc-800 bg-zinc-900/40 p-8 shadow w-full">
        <h1 className="text-3xl font-bold">React + Tailwind ✅</h1>
        <p className="mt-2 text-zinc-300">Backend status: <span className="font-semibold">{status}</span></p>
      </div>
    </div>
  );
}