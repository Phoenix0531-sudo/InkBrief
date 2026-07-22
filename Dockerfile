FROM python:3.11-slim

WORKDIR /app

COPY backend/requirements.txt ./requirements.txt
RUN pip install --no-cache-dir -r requirements.txt

# app.py imports database/config as top-level modules
COPY backend/ ./

EXPOSE 8765

CMD ["uvicorn", "app:app", "--host", "0.0.0.0", "--port", "8765"]
