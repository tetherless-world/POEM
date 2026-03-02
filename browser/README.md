# POEM Browser

## Tech Stack
Frontend: React + Vite + Tailwind  
Backend: FastAPI + rdflib

## Prerequisites
Frontend:  
Node 20+  
Backend:  
Python 3.11+  

## Frontend Setup (REACT + Vite)
cd frontend/  
npm install  
npm run dev  
Runs on http://localhost:5173  

## Backend setup (FASTAPI)
cd backend/  
python -m venv venv  
Mac/Linux: source venv/bin/activate  
Windows: venv\Scripts\activate    
pip install -r requirements.txt  
uvicorn main:app --reload   
Runs on http://127.0.0.1:8000

