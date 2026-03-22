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

In order to ge the ai summary to work you need an api key from open router
After getting your api key create a .env file in the backend folder and put your key in the file like this:
OPEN_ROUTER_KEY = YOUR_API_KEY