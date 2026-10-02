from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware

from app.database import client
from app.routes.auth import router as auth_router
from app.routes.profile import router as profile_router
from app.routes.jobs import router as jobs_router
from app.routes.recommendations import router as recommendations_router
from app.routes.applications import router as applications_router
from app.routes.notifications import router as notifications_router


app = FastAPI(
    title="Job & Internship Recommendation System",
    description="Backend API for job and internship recommendations",
    version="1.0.0"
)

app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)


app.include_router(auth_router)
app.include_router(profile_router)
app.include_router(jobs_router)
app.include_router(recommendations_router)
app.include_router(applications_router)
app.include_router(notifications_router)


@app.get("/")
def home():
    return {
        "message": "Job & Internship Recommendation System API is running"
    }


@app.get("/health")
def health():

    try:
        client.admin.command("ping")

        return {
            "status": "healthy",
            "database": "connected"
        }

    except Exception:

        return {
            "status": "unhealthy",
            "database": "disconnected"
        }