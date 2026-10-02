from pydantic import BaseModel, EmailStr,Field
from typing import Literal


class UserRegister(BaseModel):
    name: str
    email: EmailStr
    password: str = Field(min_length=8)


class UserLogin(BaseModel):
    email: EmailStr
    password: str

class CandidateProfile(BaseModel):
    skills: list[str] = []
    education: str = ""
    preferred_locations: list[str] = []
    preferred_job_type: str = "Internship"

class JobCreate(BaseModel):
    title: str = Field(min_length=2, max_length=100)
    company: str = Field(min_length=2, max_length=100)
    required_skills: list[str] = Field(min_length=1)
    location: str
    job_type: Literal["Full-time", "Internship"]
    eligibility: str = ""


class JobUpdate(BaseModel):
    title: str | None = None
    company: str | None = None
    required_skills: list[str] | None = None
    location: str | None = None
    job_type: Literal["Full-time", "Internship"] | None = None
    eligibility: str | None = None
    status: Literal["open", "closed"] | None = None

class ApplicationStatusUpdate(BaseModel):
    status: Literal["accepted", "rejected"]