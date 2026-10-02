from fastapi import APIRouter, Depends, HTTPException
from bson import ObjectId
from app.database import jobs_collection
from app.models import JobCreate, JobUpdate
from app.auth import get_current_user

router = APIRouter(
    prefix="/jobs",
    tags=["Jobs"]
)

def format_job(job: dict) -> dict:
    job["id"] = str(job.pop("_id"))
    return job

# CREATE JOB
@router.post("")
def create_job(data: JobCreate,current_user: dict = Depends(get_current_user)):
    if current_user["role"] != "recruiter":
        raise HTTPException(403, "Recruiters only")
    job = data.model_dump()

    job["recruiter_id"] = current_user["user_id"]
    job["status"] = "open"

    result = jobs_collection.insert_one(job)

    return {
        "message": "Job created successfully",
        "job_id": str(result.inserted_id)
    }


# GET ALL OPEN JOBS
@router.get("")
def get_jobs():
    jobs = jobs_collection.find({"status": "open"})
    return [format_job(job) for job in jobs]


# GET SINGLE JOB
@router.get("/{job_id}")
def get_job(job_id: str):
    if not ObjectId.is_valid(job_id):
        raise HTTPException(400, "Invalid job ID")

    job = jobs_collection.find_one({
        "_id": ObjectId(job_id),
        "status": "open"
    })

    if not job:
        raise HTTPException(404, "Job not found")

    return format_job(job)


# UPDATE JOB
@router.put("/{job_id}")
def update_job(job_id: str,data: JobUpdate,current_user: dict = Depends(get_current_user)):
    if current_user["role"] != "recruiter":
        raise HTTPException(403, "Recruiters only")

    if not ObjectId.is_valid(job_id):
        raise HTTPException(400, "Invalid job ID")

    job = jobs_collection.find_one({
        "_id": ObjectId(job_id)
    })

    if not job:
        raise HTTPException(404, "Job not found")

    if job["recruiter_id"] != current_user["user_id"]:
        raise HTTPException(403, "You can only update your own jobs")

    updates = data.model_dump(exclude_unset=True)

    if not updates:
        raise HTTPException(400, "No fields provided for update")

    jobs_collection.update_one(
        {"_id": ObjectId(job_id)},
        {"$set": updates}
    )

    return {"message": "Job updated successfully"}

# DELETE JOB
@router.delete("/{job_id}")
def delete_job(job_id: str,current_user: dict = Depends(get_current_user)):
    if current_user["role"] != "recruiter":
        raise HTTPException(403, "Recruiters only")

    if not ObjectId.is_valid(job_id):
        raise HTTPException(400, "Invalid job ID")

    job = jobs_collection.find_one({
        "_id": ObjectId(job_id)
    })

    if not job:
        raise HTTPException(404, "Job not found")

    if job["recruiter_id"] != current_user["user_id"]:
        raise HTTPException(403, "You can only delete your own jobs")

    jobs_collection.delete_one({
        "_id": ObjectId(job_id)
    })

    return {"message": "Job deleted successfully"}