# blog/views.py
from django.shortcuts import render, get_object_or_404, redirect
from django.utils import timezone
from .models import Post
from .forms import PostForm
from rest_framework import viewsets
from .serializers import PostSerializer


# ──────────────────────────────
# 1️⃣ Django REST Framework API ViewSet
# ──────────────────────────────
class PostViewSet(viewsets.ModelViewSet):
    """
    /api_root/posts/ 엔드포인트 제공
    GET - 게시물 목록 조회
    POST - 새 게시물(이미지 포함) 등록
    """
    queryset = Post.objects.all().order_by('-published_date')
    serializer_class = PostSerializer


# ──────────────────────────────
# 2️⃣ Django 템플릿용 뷰 함수
# ──────────────────────────────
def post_list(request):
    posts = Post.objects.all().order_by('-created_date')

    return render(request, 'blog/post_list.html', {'posts': posts})


def post_detail(request, pk):
    post = get_object_or_404(Post, pk=pk)
    return render(request, 'blog/post_detail.html', {'post': post})


def post_new(request):
    if request.method == "POST":
        form = PostForm(request.POST, request.FILES)  # ✅ 이미지 포함
        if form.is_valid():
            post = form.save(commit=False)
            post.author = request.user
            if not post.published_date:
                post.published_date = timezone.now()
            post.published_date = timezone.now()
            post.save()
            return redirect('post_detail', pk=post.pk)
    else:
        form = PostForm()
    return render(request, 'blog/post_edit.html', {'form': form})


def post_edit(request, pk):
    post = get_object_or_404(Post, pk=pk)
    if request.method == "POST":
        form = PostForm(request.POST, request.FILES, instance=post)  # ✅ 이미지 포함
        if form.is_valid():
            post = form.save(commit=False)
            post.author = request.user
            if not post.published_date:
                post.published_date = timezone.now()
            post.published_date = timezone.now()
            post.save()
            return redirect('post_detail', pk=post.pk)
    else:
        form = PostForm(instance=post)
    return render(request, 'blog/post_edit.html', {'form': form})
def js_test(request):
    return render(request, 'blog/js_test.html')