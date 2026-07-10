"""InkBrief Backend — FastAPI entry point."""

import uvicorn
from fastapi import FastAPI
from contextlib import asynccontextmanager
from database import initialize_database
from config import PORT, HOST


@asynccontextmanager
async def lifespan(app: FastAPI):
    # Startup
    initialize_database()
    print(f"[InkBrief] Database initialized")
    yield
    # Shutdown
    print(f"[InkBrief] Shutting down")


app = FastAPI(
    title="InkBrief",
    description="Personal information briefing terminal backend",
    version="0.1.0",
    lifespan=lifespan,
)

# Import and register routes
from api import router as api_router
app.include_router(api_router)


if __name__ == "__main__":
    print(f"[InkBrief] Starting on {HOST}:{PORT}")
    uvicorn.run("app:app", host=HOST, port=PORT, reload=False)
