# Ant-Sight_BE
CAU CSE Capstone Project(2) project
# Stock Data Crawling & Processing Pipeline (Ant-Sight)

A scalable data pipeline system that collects, processes, and stores large-scale stock discussion data from Naver Finance.

## Overview

This project is designed to handle large-scale data collection and processing by integrating a web crawler, data cleaning pipeline, and backend API server.

- Crawls stock discussion data from Naver Finance
- Processes and cleans raw text data
- Stores structured data into PostgreSQL via REST API
- Handles failures with retry logic and checkpoint-based recovery

---

## Architecture


[Crawler] → [Data Cleaning] → [REST API] → [PostgreSQL DB]


- **Crawler (Python)**: Collects raw data from multiple stock pages
- **Cleaner (Python)**: Transforms raw data into structured format
- **Backend (Spring Boot)**: Receives and stores data via REST API
- **Database (PostgreSQL)**: Stores processed data

---

## Key Features

-  Large-scale crawling (4,000+ stock codes)
-  Automated data pipeline
-  Retry logic & timeout handling for robust requests
-  Checkpoint system for fault tolerance and recovery
-  Bulk API processing for efficient data storage
-  Duplicate prevention using database constraints

---

## Tech Stack

**Backend**
- Java, Spring Boot
- REST API

**Data Pipeline**
- Python (Web Crawling, Data Processing)

**Database**
- PostgreSQL

**Tools**
- Git, Docker

---

## Project Structure


crawler : crawler.py │ cleaner.py │ storage.py │ checkpoint.py │ pipeline.py │ stock_fetcher.py                                           
backend : PostController.java │ PostService.java │ PostRepository.java │ Post.java │ PostDto.java                           
README.md


---

## How It Works

1. Crawl stock discussion data using Python crawler  
2. Clean and structure the raw data  
3. Send processed data to backend API  
4. Store data in PostgreSQL database  

---

## Results

- Crawled data from over 4,000 stock codes
- Collected thousands of discussion posts across multiple years
- Successfully stored structured data into PostgreSQL via REST API
- Achieved stable performance with retry logic and checkpoint recovery

---

## Why This Project

This project demonstrates the ability to design and implement a scalable data pipeline that connects data collection, processing, and storage systems — a core requirement for AI-driven services.

## What I Learned

- Designing scalable data pipelines
- Handling real-world data and edge cases
- Building REST APIs for data processing systems
- Using AI tools (ChatGPT, Claude) to improve development efficiency
- Implementing robust error handling and recovery systems

---

## Repository

GitHub: https://github.com/capstone-26-1/Ant-Sight_BE

---

## Future Improvements

- Add data visualization dashboard
- Integrate machine learning for sentiment analysis
- Optimize crawling performance with parallel processing
